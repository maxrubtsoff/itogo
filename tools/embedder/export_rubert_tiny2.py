"""
One-off export of cointegrated/rubert-tiny2 to an int8 ONNX model for the app.

Writes app/src/main/assets/embedder/model.onnx and vocab.txt, then prints
reference token ids used by WordPieceTokenizerTest and a few cosine checks.

    python -m venv <scratch>/venv
    <scratch>/venv/Scripts/pip install -r tools/embedder/requirements.txt
    <scratch>/venv/Scripts/python tools/embedder/export_rubert_tiny2.py
"""
import shutil
import tempfile
from pathlib import Path

import numpy as np
import onnxruntime as ort
from onnxruntime.quantization import QuantType, quantize_dynamic
from optimum.onnxruntime import ORTModelForFeatureExtraction
from transformers import AutoTokenizer

MODEL = "cointegrated/rubert-tiny2"
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "src" / "main" / "assets" / "embedder"
SAMPLES = ["Медицинские услуги", "Связь, интернет и ТВ", "Lamoda"]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    tok = AutoTokenizer.from_pretrained(MODEL)
    with tempfile.TemporaryDirectory() as tmp:
        ORTModelForFeatureExtraction.from_pretrained(MODEL, export=True).save_pretrained(tmp)
        quantize_dynamic(str(Path(tmp) / "model.onnx"), str(OUT / "model.onnx"), weight_type=QuantType.QInt8)
        tok.save_pretrained(tmp)
        shutil.copy(Path(tmp) / "vocab.txt", OUT / "vocab.txt")

    size_mb = (OUT / "model.onnx").stat().st_size / 1e6
    print(f"model.onnx: {size_mb:.1f} MB")

    sess = ort.InferenceSession(str(OUT / "model.onnx"))
    print("inputs:", [i.name for i in sess.get_inputs()])
    print("outputs:", [o.name for o in sess.get_outputs()])
    for s in SAMPLES:
        print(repr(s), tok(s)["input_ids"])

    def embed(texts):
        enc = tok(texts, padding=True, return_tensors="np")
        feeds = {i.name: enc[i.name].astype(np.int64) for i in sess.get_inputs()}
        cls = sess.run(None, feeds)[0][:, 0, :]
        return cls / np.linalg.norm(cls, axis=1, keepdims=True)

    v = embed(["Здоровье", "Медицинские услуги", "АЗС"])
    print("cos(здоровье, медуслуги) =", float(v[0] @ v[1]))
    print("cos(здоровье, азс)       =", float(v[0] @ v[2]))


if __name__ == "__main__":
    main()
