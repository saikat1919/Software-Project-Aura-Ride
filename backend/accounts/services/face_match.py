"""
Face matching + OCR cross-check — PROJECT_SPEC §7.2 / §7.3.

Runs synchronously in the register/step-up request (§17.5). Behind the DEMO_FAKE_AI
seam so the backend runs with no TensorFlow. When real AI is on, DeepFace (Facenet512 +
RetinaFace) detects and crops the face in EACH image first — so the selfie is compared to
the NID *portrait*, not the whole card — with enforce_detection=True wrapped so a detector
miss becomes REVIEW, never a 500 on the registration pipeline (eng review OV1).

Thresholds are calibrated for selfie-vs-NID on limited real pairs (§7.2.1), biased to
REVIEW. Gender is estimated from the selfie (BD NID has no gender field).
"""
from django.conf import settings

# Facenet512 (~90MB) — higher accuracy than SFace for selfie-vs-ID. RetinaFace detector
# finds and crops the actual FACE in each image first, so we compare the selfie to the NID
# *portrait*, not the whole card (which was scoring ~0.29). enforce_detection=True: if no
# face is found in either image we get an exception -> REVIEW, never a false FAIL (§7.2, OV1).
MODEL_NAME = "Facenet512"
DETECTOR_BACKEND = "retinaface"
# The liveness selfie is already a centered face, so skip detection for the gender pass —
# OpenCV's eye-detector cascade blew memory (OutOfMemoryError) on full-res phone selfies.
GENDER_DETECTOR = "skip"

# Calibrated cosine bands for selfie-vs-Bangladeshi-NID (§7.2.1). DeepFace's default 0.30
# threshold is for two normal photos; a GENUINE selfie-vs-ID here measures ~0.6 (old/low-res
# ID photo, different lighting), while different people run ~0.9+. So we ignore the default
# and use wider bands, biased to REVIEW — only a clear match auto-PASSes, only a clear
# mismatch auto-FAILs, everything between reaches the admin (who sees both photos).
# Tuned on limited real pairs; widen CAL_PASS_MAX / narrow CAL_FAIL_MIN as you gather more.
CAL_PASS_MAX = 0.62   # cosine <= this -> PASS (confident same person)
CAL_FAIL_MIN = 0.90   # cosine >= this -> FAIL (confident different person)

PASS, REVIEW, FAIL = "PASS", "REVIEW", "FAIL"


def score_faces(selfie_path: str, nid_portrait_path: str):
    """Return (score: float|None, verdict: str). Never raises (OV1)."""
    if settings.DEMO_FAKE_AI:
        # Canned "clear match" so the flow demos without real models.
        return 0.82, PASS
    try:
        from deepface import DeepFace
        result = DeepFace.verify(
            img1_path=selfie_path,
            img2_path=nid_portrait_path,
            model_name=MODEL_NAME,
            detector_backend=DETECTOR_BACKEND,  # detect + crop the face in each image
            distance_metric="cosine",
            enforce_detection=True,             # no face found -> exception -> REVIEW (not a false FAIL)
            align=True,
        )
        distance = float(result["distance"])
        if distance <= CAL_PASS_MAX:
            verdict = PASS
        elif distance >= CAL_FAIL_MIN:
            verdict = FAIL
        else:
            verdict = REVIEW
        # 0..1 similarity for display: 1 at the PASS cutoff, 0 at the FAIL cutoff.
        span = CAL_FAIL_MIN - CAL_PASS_MAX
        score = round(min(1.0, max(0.0, (CAL_FAIL_MIN - distance) / span)), 3)
        return score, verdict
    except Exception:
        # No detectable face, unreadable image, or model error -> human review, never a 500.
        return None, REVIEW


def estimate_gender(selfie_path: str):
    """Best-effort gender guess from the selfie (§7.3, women-only). Returns
    (label, confidence_pct, is_male) or (None, None, None). Never raises — it's a soft
    signal for the admin, never an auto-reject."""
    if settings.DEMO_FAKE_AI or not selfie_path:
        return None, None, None
    try:
        from deepface import DeepFace
        res = DeepFace.analyze(
            img_path=selfie_path, actions=("gender",),
            detector_backend=GENDER_DETECTOR, enforce_detection=False,
        )
        face = res[0] if isinstance(res, list) else res
        label = face["dominant_gender"]                 # "Man" | "Woman"
        conf = int(round(float(face["gender"][label]))) # 0..100
        return label, conf, (label == "Man")
    except Exception:
        return None, None, None


def warm() -> None:
    """Preload the model at startup so the first real request isn't a cold cliff
    (eng review Issue 4). No-op when AI is faked."""
    if settings.DEMO_FAKE_AI:
        return
    try:
        from deepface import DeepFace
        DeepFace.build_model(task="facial_recognition", model_name=MODEL_NAME)
        DeepFace.build_model(task="facial_attribute", model_name="Gender")
    except Exception:
        pass  # best-effort; real calls still work (just slower first time).


def cross_check(typed_name: str, ocr_name: str, ocr_gender: str, nid_number: str,
                license_name: str = "", is_driver: bool = False) -> dict:
    """OCR cross-check flags (§7.3). Never auto-fails — flags reach the admin."""
    def norm(s):
        return "".join((s or "").lower().split())

    flags = {}
    if ocr_name and typed_name and norm(ocr_name) != norm(typed_name):
        flags["name_mismatch"] = True
    # Gender is NOT on the Bangladeshi NID — we judge it from the selfie instead
    # (estimate_gender + face_looks_male flag), not from OCR text.
    if not (nid_number or "").strip():
        flags["nid_number_missing"] = True
    if is_driver and license_name and typed_name and norm(license_name) != norm(typed_name):
        flags["license_mismatch"] = True
    return flags
