import json

log_path = "/Users/krishan/.gemini/antigravity-ide/brain/5284e8ea-69e3-4879-b1ab-241ceb1d1445/.system_generated/logs/transcript.jsonl"
output_path = "/Users/krishan/.gemini/antigravity-ide/brain/0cf5ecc7-f365-4e06-8dad-60920784def5/scratch/reconstructed_working_main.java"

with open(log_path, 'r') as f:
    steps = [json.loads(line) for line in f]

file_content = ""

# Track step index and apply tools
for idx, step in enumerate(steps):
    tool_calls = step.get("tool_calls", [])
    for tc in tool_calls:
        name = tc.get("name")
        args = tc.get("args", {})
        
        # We only care about modifications to MainActivity.java
        target = args.get("TargetFile") or args.get("Target")
        if not target or "MainActivity.java" not in target:
            continue
            
        print(f"Applying step index {idx} ({step.get('step_index')}): {name}")
        
        if name == "write_to_file":
            file_content = args.get("CodeContent", "")
            
        elif name in ("replace_file_content", "multi_replace_file_content"):
            chunks = args.get("ReplacementChunks", [])
            if isinstance(chunks, str):
                try:
                    chunks = json.loads(chunks, strict=False)
                except Exception as e:
                    print(f"Failed to parse chunks normally in step {idx}: {e}")
                    # Try cleaning up quotes or control chars
                    cleaned = chunks.replace('\n', '\\n').replace('\r', '\\r')
                    chunks = json.loads(cleaned, strict=False)
            elif name == "replace_file_content" and not chunks:
                chunks = [{
                    "TargetContent": args.get("TargetContent"),
                    "ReplacementContent": args.get("ReplacementContent")
                }]
                
            for chunk in chunks:
                target_str = chunk.get("TargetContent")
                replace_str = chunk.get("ReplacementContent")
                
                # Check for exact matches
                if target_str not in file_content:
                    print(f"Warning: target string not found exactly in step {idx}!")
                    # Try standardizing line endings
                    normalized_file = file_content.replace('\r\n', '\n')
                    normalized_target = target_str.replace('\r\n', '\n')
                    if normalized_target in normalized_file:
                        print("Found match with normalized line endings!")
                        file_content = normalized_file.replace(normalized_target, replace_str.replace('\r\n', '\n'))
                        continue
                    else:
                        print("Failed to match target even with normalization!")
                        print(f"Target was:\n{target_str[:100]}...\n")
                
                file_content = file_content.replace(target_str, replace_str)

with open(output_path, 'w') as out:
    out.write(file_content)

print(f"Reconstructed MainActivity written to: {output_path}")
print(f"Length of reconstructed code: {len(file_content)} characters")
