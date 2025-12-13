import argparse
import asyncio
import logging
import os
import re
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional
import shutil
import uuid

import torch
import uvicorn
from fastapi import FastAPI, HTTPException, File, UploadFile, Form, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from peft import PeftModel
from pydantic import BaseModel, Field
from transformers import AutoModelForCausalLM, AutoTokenizer
# 在DeploymentConfig类中添加（大约在DeploymentConfig类的__init__方法中）
import json
import datetime

from onlinelora import run_training

# 在配置默认值中添加
DEFAULT_LOG_FILE = "model_logs.json"
DEFAULT_ENABLE_LOGGING = True
DEFAULT_DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
DEFAULT_BASE_MODEL = "Qwen/Qwen3-0.6B"
DEFAULT_ADAPTER_PATH = "qwen3-0.6B-lora-products/checkpoint-step-5000"
LATEST_ADAPTER_PATH_FILE = "latest_adapter_path.txt"
DEFAULT_MAX_NEW_TOKENS = 384
DEFAULT_TEMPERATURE = 0.7
DEFAULT_TOP_P = 0.9
DEFAULT_DO_SAMPLE = True
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8000
DEFAULT_TRAINING_OUTPUT_DIR = "./qwen3-retrained-products"

logging.basicConfig(
	level=logging.INFO,
	format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger(__name__)

THINK_PATTERN = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)


def _parse_bool(value: Optional[str], default: bool) -> bool:
	if value is None:
		return default
	return value.strip().lower() in {"1", "true", "yes", "y"}


def _parse_int(value: Optional[str], default: int, name: str) -> int:
	if value is None:
		return default
	try:
		return int(value)
	except ValueError as exc:
		raise ValueError(f"Invalid integer for {name}: {value}") from exc


def _parse_float(value: Optional[str], default: float, name: str) -> float:
	if value is None:
		return default
	try:
		return float(value)
	except ValueError as exc:
		raise ValueError(f"Invalid float for {name}: {value}") from exc


def _normalize_device(value: Optional[str]) -> str:
	if value is None or not value.strip():
		return DEFAULT_DEVICE
	candidate = value.strip()
	if candidate.lower() == "auto":
		return "cuda" if torch.cuda.is_available() else "cpu"
	return candidate


def clean_generated_text(text: str) -> str:
	if not text:
		return ""
	cleaned = THINK_PATTERN.sub("", text)
	return cleaned.strip()


def get_initial_adapter_path() -> str:
	if os.path.exists(LATEST_ADAPTER_PATH_FILE):
		with open(LATEST_ADAPTER_PATH_FILE, "r", encoding="utf-8") as f:
			path = f.read().strip()
			if path and os.path.isdir(path):
				logger.info(f"Found latest adapter path from file: {path}")
				return path
	logger.info(f"Using default adapter path: {DEFAULT_ADAPTER_PATH}")
	return DEFAULT_ADAPTER_PATH


@dataclass
class DeploymentConfig:
	base_model: str = DEFAULT_BASE_MODEL
	adapter_path: str = "" # Will be set by factory methods
	device: str = DEFAULT_DEVICE
	host: str = DEFAULT_HOST
	port: int = DEFAULT_PORT
	max_new_tokens: int = DEFAULT_MAX_NEW_TOKENS
	temperature: float = DEFAULT_TEMPERATURE
	top_p: float = DEFAULT_TOP_P
	do_sample: bool = DEFAULT_DO_SAMPLE
	log_file: str = DEFAULT_LOG_FILE
	enable_logging: bool = DEFAULT_ENABLE_LOGGING
	training_output_dir: str = DEFAULT_TRAINING_OUTPUT_DIR
   
        

	@classmethod
	def from_env(cls) -> "DeploymentConfig":
		initial_adapter_path = get_initial_adapter_path()
		return cls(
			base_model=os.getenv("BASE_MODEL", cls.base_model),
			adapter_path=os.getenv("ADAPTER_PATH", initial_adapter_path),
			device=_normalize_device(os.getenv("DEVICE")),
			host=os.getenv("API_HOST", cls.host),
			port=_parse_int(os.getenv("API_PORT"), cls.port, "API_PORT"),
			max_new_tokens=_parse_int(
				os.getenv("MAX_NEW_TOKENS"), cls.max_new_tokens, "MAX_NEW_TOKENS"
			),
			temperature=_parse_float(
				os.getenv("TEMPERATURE"), cls.temperature, "TEMPERATURE"
			),
			top_p=_parse_float(os.getenv("TOP_P"), cls.top_p, "TOP_P"),
			do_sample=_parse_bool(os.getenv("DO_SAMPLE"), cls.do_sample),
			log_file=os.getenv("LOG_FILE", cls.log_file),
            enable_logging=_parse_bool(os.getenv("ENABLE_LOGGING"), cls.enable_logging),
		)

	@classmethod
	def from_args(cls) -> "DeploymentConfig":
		initial_adapter_path = get_initial_adapter_path()
		parser = argparse.ArgumentParser(
			description="Serve the LoRA fine-tuned Qwen model via a REST API."
		)
		parser.add_argument("--base-model", default=cls.base_model, help="Base model id or path.")
		parser.add_argument(
			"--adapter-path",
			default=initial_adapter_path,
			help="Path to the trained LoRA adapter checkpoint.",
		)
		parser.add_argument(
			"--device",
			default=None,
			help="Device to run inference on (cpu, cuda, cuda:0, auto).",
		)
		parser.add_argument("--host", default=cls.host, help="Host interface for the REST server.")
		parser.add_argument(
			"--port",
			type=int,
			default=cls.port,
			help="Port for the REST server.",
		)
		parser.add_argument(
			"--max-new-tokens",
			type=int,
			default=cls.max_new_tokens,
			help="Default max new tokens per generation.",
		)
		parser.add_argument(
			"--temperature",
			type=float,
			default=cls.temperature,
			help="Default sampling temperature.",
		)
		parser.add_argument(
			"--top-p",
			type=float,
			default=cls.top_p,
			help="Default nucleus sampling top-p value.",
		)
		parser.add_argument(
			"--do-sample",
			action="store_true",
			help="Enable sampling (otherwise greedy).",
		)
		args = parser.parse_args()
		return cls(
			base_model=args.base_model,
			adapter_path=args.adapter_path,
			device=_normalize_device(args.device),
			host=args.host,
			port=args.port,
			max_new_tokens=args.max_new_tokens,
			temperature=args.temperature,
			top_p=args.top_p,
			do_sample=args.do_sample or cls.do_sample,
		)


class GenerateRequest(BaseModel):
	prompt: str = Field(..., min_length=1)
	max_new_tokens: Optional[int] = Field(
		None, gt=0, description="Overrides the default max token count."
	)
	temperature: Optional[float] = Field(
		None, gt=0.0, le=2.0, description="Overrides the default temperature."
	)
	top_p: Optional[float] = Field(
		None, gt=0.0, le=1.0, description="Overrides the default nucleus sampling value."
	)
	do_sample: Optional[bool] = Field(
		None, description="Set to true to enable sampling or false for greedy decoding."
	)


class GenerateResponse(BaseModel):
	prediction: str


class HealthResponse(BaseModel):
	status: str
	device: str
	model: str
	adapter: str


@dataclass
class GenerationResult:
	prediction: str


class ModelService:
	def __init__(self, config: DeploymentConfig) -> None:
		self.config = config
		resolved_device = _normalize_device(config.device)
		self.device = torch.device(resolved_device)
		if self.device.type == "cuda" and not torch.cuda.is_available():
			raise RuntimeError("CUDA requested but not available on this machine.")
		self.model = None
		self.tokenizer = None
		self._lock = threading.Lock()
		self._training_lock = threading.Lock() # Lock for serializing training
		self._log_lock = threading.Lock()
		self._log_path = Path(self.config.log_file)
		self._enable_log = self.config.enable_logging
		self._load()

	def _write_log(self, payload: "GenerateRequest", prediction: str) -> None:
		if not self._enable_log:
			return
		record = {
			"timestamp": datetime.datetime.utcnow().isoformat() + "Z",
			"prompt": payload.prompt,
			"max_new_tokens": payload.max_new_tokens if payload.max_new_tokens is not None else self.config.max_new_tokens,
			"temperature": payload.temperature if payload.temperature is not None else self.config.temperature,
			"top_p": payload.top_p if payload.top_p is not None else self.config.top_p,
			"do_sample": payload.do_sample if payload.do_sample is not None else self.config.do_sample,
			"prediction": prediction,
		}
		line = json.dumps(record, ensure_ascii=False)
		try:
			with self._log_lock:
				self._log_path.parent.mkdir(parents=True, exist_ok=True)
				with self._log_path.open("a", encoding="utf-8") as fh:
					fh.write(line + "\n")
		except Exception as err:
			logger.exception("Failed to write inference log: %s", err)

	def _load(self) -> None:
		logger.info("Loading tokenizer from %s", self.config.adapter_path)
		torch_dtype = torch.float16 if self.device.type == "cuda" else torch.float32
		self.tokenizer = AutoTokenizer.from_pretrained(
			self.config.adapter_path,
			trust_remote_code=True,
		)
		if self.tokenizer.pad_token is None:
			self.tokenizer.pad_token = self.tokenizer.eos_token
		logger.info("Loading base model %s", self.config.base_model)
		base_model = AutoModelForCausalLM.from_pretrained(
			self.config.base_model,
			torch_dtype=torch_dtype,
			trust_remote_code=True,
		)
		base_model.to(self.device)
		logger.info("Applying LoRA adapter from %s", self.config.adapter_path)
		self.model = PeftModel.from_pretrained(
			base_model,
			self.config.adapter_path,
			torch_dtype=torch_dtype,
		)
		self.model.to(self.device)
		self.model.eval()
		logger.info("Model ready on %s", self.device)

	def close(self) -> None:
		self.model = None
		self.tokenizer = None
		if self.device.type == "cuda":
			torch.cuda.empty_cache()

	def reload_model(self, new_adapter_path: str) -> None:
		with self._lock:
			logger.info("Reloading model with new adapter: %s", new_adapter_path)
			self.config.adapter_path = new_adapter_path
			self.close()
			self._load()
			logger.info("Model reloaded successfully.")
			# Save the latest path
			try:
				with open(LATEST_ADAPTER_PATH_FILE, "w", encoding="utf-8") as f:
					f.write(new_adapter_path)
				logger.info(f"Saved latest adapter path to {LATEST_ADAPTER_PATH_FILE}")
			except IOError as e:
				logger.error(f"Failed to save latest adapter path: {e}")

	async def generate(self, request: GenerateRequest) -> GenerationResult:
		loop = asyncio.get_running_loop()
		return await loop.run_in_executor(None, self._generate_with_lock, request)

	def _generate_with_lock(self, request: GenerateRequest) -> GenerationResult:
		with self._lock:
			return self._generate_sync(request)

	def _generate_sync(self, request: GenerateRequest) -> GenerationResult:
		if self.model is None or self.tokenizer is None:
			raise RuntimeError("Model service is not initialized.")
		max_new_tokens = request.max_new_tokens or self.config.max_new_tokens
		if max_new_tokens <= 0:
			raise ValueError("max_new_tokens must be positive.")
		temperature = request.temperature if request.temperature is not None else self.config.temperature
		top_p = request.top_p if request.top_p is not None else self.config.top_p
		do_sample = request.do_sample if request.do_sample is not None else self.config.do_sample
		messages = [{"role": "user", "content": request.prompt}]
		inputs = self.tokenizer.apply_chat_template(
            messages,
            add_generation_prompt=True,
            return_tensors="pt",
            return_dict=True,
        ).to(self.device)
		with torch.no_grad():
			outputs = self.model.generate(
				**inputs,
				max_new_tokens=max_new_tokens,
				temperature=temperature,
				top_p=top_p,
				do_sample=do_sample,
				pad_token_id=self.tokenizer.pad_token_id,
				eos_token_id=self.tokenizer.eos_token_id
            )
		generated_ids = outputs[0, inputs["input_ids"].shape[-1] :]
		response = self.tokenizer.decode(
            generated_ids, skip_special_tokens=True, clean_up_tokenization_spaces=True
        ).strip()
        
		
		cleaned = clean_generated_text(response)
		self._write_log(request, cleaned)

		return GenerationResult(prediction=cleaned)


def create_app(config: DeploymentConfig) -> FastAPI:
	logger.info("Initializing REST API")
	service = ModelService(config)
	api = FastAPI(title="Qwen LoRA Inference API", version="1.0.0")
	api.add_middleware(
		CORSMiddleware,
		allow_origins=["*"],
		allow_credentials=True,
		allow_methods=["*"],
		allow_headers=["*"],
	)
	api.state.service = service
	api.state.config = config

	@api.get("/health", response_model=HealthResponse)
	async def health() -> HealthResponse:
		return HealthResponse(
			status="ok",
			device=str(service.device),
			model=config.base_model,
			adapter=config.adapter_path,
		)

	@api.post("/v1/generate", response_model=GenerateResponse)
	async def generate_text(payload: GenerateRequest) -> GenerateResponse:
		try:
			result = await service.generate(payload)
		except ValueError as exc:
			raise HTTPException(status_code=400, detail=str(exc)) from exc
		except RuntimeError as exc:
			raise HTTPException(status_code=500, detail=str(exc)) from exc
		return GenerateResponse(prediction=result.prediction)

	def training_task(train_file_path: str, service: ModelService, config: DeploymentConfig):
		with service._training_lock:
			try:
				logger.info("Acquired training lock. Starting background training task.")
				# Get the most current adapter path inside the lock
				current_adapter_path = service.config.adapter_path
				logger.info(f"Training will build on adapter: {current_adapter_path}")

				output_dir = f"{config.training_output_dir}-{uuid.uuid4()}"
				final_checkpoint = run_training(
					train_data_path=train_file_path,
					base_model_id=config.base_model,
					output_dir=output_dir,
					existing_adapter_path=current_adapter_path,
				)
				logger.info(f"Training finished. New checkpoint at {final_checkpoint}")
				service.reload_model(final_checkpoint)
			except Exception as e:
				logger.error(f"An error occurred during training: {e}")
			finally:
				# Clean up the temporary training file
				os.remove(train_file_path)
				logger.info(f"Removed temporary training file: {train_file_path}")
				logger.info("Released training lock.")


	@api.post("/v1/retrain")
	async def retrain_model(
		background_tasks: BackgroundTasks,
		retrain: bool = Form(...),
		file: UploadFile = File(...),
	):
		if not retrain:
			return {"message": "Retraining not requested."}
		if not file.filename.endswith(".json"):
			raise HTTPException(status_code=400, detail="Invalid file type. Only .json files are accepted.")

		temp_dir = Path("temp_data")
		temp_dir.mkdir(exist_ok=True)
		
		# Create a unique filename to avoid conflicts
		unique_filename = f"{uuid.uuid4()}_{file.filename}"
		temp_file_path = temp_dir / unique_filename

		try:
			with temp_file_path.open("wb") as buffer:
				shutil.copyfileobj(file.file, buffer)
		finally:
			file.file.close()

		background_tasks.add_task(training_task, str(temp_file_path), api.state.service, api.state.config)

		return {"message": "Retraining started in the background. The model will be updated upon completion."}

	@api.on_event("shutdown")
	async def shutdown_event() -> None:
		service.close()

	return api


app: Optional[FastAPI] = None
if __name__ != "__main__":
	app = create_app(DeploymentConfig.from_env())


def main() -> None:
	config = DeploymentConfig.from_args()
	logger.info("Starting server on %s:%s (device=%s)", config.host, config.port, config.device)
	uvicorn.run(
		create_app(config),
		host=config.host,
		port=config.port,
		log_level="info",
	)


if __name__ == "__main__":
	main()
