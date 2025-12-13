import os
import json
import random
from pathlib import Path
from datasets import load_dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    DataCollatorForLanguageModeling,
    Trainer,
    TrainingArguments,
)
from peft import LoraConfig, get_peft_model, TaskType, PeftModel

def transform_data(original_path: str) -> str:
    """
    Transforms the input JSON data from product lists to input/output examples.
    """
    with open(original_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    transactions = data.get("Product", [])
    examples = []
    for items in transactions:
        if len(items) < 2:
            continue
        # Create examples for all permutations of anchor/companion
        for i in range(len(items)):
            anchor_item = items[i]
            companion_items = [item for j, item in enumerate(items) if i != j]
            
            if not companion_items:
                continue

            instruction = (
                f"顾客已购买「{anchor_item}」，"
                "请推测该顾客还可能一起购买的其他商品名称。"
            )
            output_text = "，".join(companion_items)
            examples.append({"input": instruction, "output": output_text})

    # Deduplicate
    seen = set()
    deduped_data = []
    for item in examples:
        key = (item.get("input", ""), item.get("output", ""))
        if key not in seen:
            seen.add(key)
            deduped_data.append(item)

    # Save to a new temporary file
    temp_dir = Path(os.path.dirname(original_path))
    temp_path = temp_dir / f"transformed_{Path(original_path).name}"
    with open(temp_path, "w", encoding="utf-8") as f:
        json.dump(deduped_data, f, ensure_ascii=False, indent=2)
        
    return str(temp_path)

def run_training(train_data_path: str, base_model_id: str, output_dir: str, existing_adapter_path: str, num_train_epochs: int = 3):
    """
    Runs the fine-tuning training process, potentially from an existing adapter.

    Args:
        train_data_path (str): Path to the training data JSON file.
        base_model_id (str): The base model ID from Hugging Face.
        output_dir (str): The directory to save training outputs and checkpoints.
        existing_adapter_path (str): Path to the existing LoRA adapter to continue training from.
        num_train_epochs (int): The number of training epochs.
    """
    
    transformed_data_path = None
    try:
        # Transform the data first
        print(f"Transforming data from {train_data_path}...")
        transformed_data_path = transform_data(train_data_path)
        print(f"Transformed data saved to {transformed_data_path}")

        tokenizer = AutoTokenizer.from_pretrained(base_model_id, trust_remote_code=True)
        base_model = AutoModelForCausalLM.from_pretrained(base_model_id, trust_remote_code=True)

        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token
        base_model.config.pad_token_id = tokenizer.pad_token_id

        # Load existing LoRA adapter
        model = PeftModel.from_pretrained(base_model, existing_adapter_path, is_trainable=True)
        print(f"Loaded adapter from {existing_adapter_path} and set to trainable.")

        raw_dataset = load_dataset("json", data_files={"train": transformed_data_path})
        raw_dataset["train"] = raw_dataset["train"].shuffle(seed=42)
        max_seq_length = 512

        def tokenize_chat(example):
            user_prompt = example.get("input") or ""
            assistant_response = example.get("output") or ""
            messages = [
                {"role": "user", "content": user_prompt},
                {"role": "assistant", "content": assistant_response},
            ]
            chat_text = tokenizer.apply_chat_template(
                messages,
                add_generation_prompt=False,
                tokenize=False,
            )
            tokenized = tokenizer(
                chat_text,
                truncation=True,
                max_length=max_seq_length,
                padding="max_length",
                return_attention_mask=True,
                return_offsets_mapping=True,
            )

            labels = tokenized["input_ids"].copy()
            offsets = tokenized["offset_mapping"]
            assistant_start = chat_text.find(assistant_response)

            if assistant_start != -1:
                for idx, (start, end) in enumerate(offsets):
                    if start is None:
                        continue
                    if start >= assistant_start:
                        break
                    labels[idx] = -100
            else:
                labels = [-100 for _ in labels]

            tokenized.pop("offset_mapping")
            tokenized["labels"] = labels
            return tokenized

        tokenized_dataset = raw_dataset.map(
            tokenize_chat,
            remove_columns=raw_dataset["train"].column_names,
            desc=f"Tokenizing {transformed_data_path}",
        )

        data_collator = DataCollatorForLanguageModeling(
            tokenizer=tokenizer,
            mlm=False,
            pad_to_multiple_of=8,
        )

        model.print_trainable_parameters()

        training_args = TrainingArguments(
        output_dir=output_dir,
        per_device_train_batch_size=2,
        gradient_accumulation_steps=1,
        num_train_epochs=num_train_epochs,
        learning_rate=5e-4,
        fp16=True,
        logging_steps=50,
        save_strategy="epoch",
        save_total_limit=3, # Keep only the last 3 checkpoints
        report_to="none",
        )
            
        trainer = Trainer(
            model=model,
            args=training_args,
            train_dataset=tokenized_dataset["train"],
            data_collator=data_collator,
        )
        trainer.train()
        
        # After training, save the final model
        final_checkpoint_dir = os.path.join(output_dir, "final_checkpoint")
        trainer.save_model(final_checkpoint_dir)
        tokenizer.save_pretrained(final_checkpoint_dir)
        print(f"Final model saved at {final_checkpoint_dir}")
        return final_checkpoint_dir
    finally:
        # Clean up the transformed data file
        if transformed_data_path and os.path.exists(transformed_data_path):
            os.remove(transformed_data_path)
            print(f"Cleaned up temporary file: {transformed_data_path}")
