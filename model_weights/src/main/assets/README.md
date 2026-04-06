# Model Weights Asset Pack

Place release model artifacts for Play Asset Delivery in this folder.

Use an unzipped model directory layout in assets (not a ZIP file).

Required target folder name for the current runtime contract:
- `Qwen3-0.6B-q4f16_1-MLC/`

At runtime, `LlmPipeline` still loads from:
- `/data/data/com.example.nemebudget/files/Qwen3-0.6B-q4f16_1-MLC`

So release flow copies the bundled model directory from this asset pack into `context.filesDir` on first run.

ZIP import is kept as a fallback path for manual recovery/testing.


