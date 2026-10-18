"""配置加载：从 server/.env 读取（该文件含密钥，已被 .gitignore 排除），环境变量优先。"""
import os
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parent.parent


def _load_env(path: Path) -> dict:
    env = {}
    if not path.exists():
        return env
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        env[key.strip()] = value.strip().strip("'\"")
    return env


_env = _load_env(SERVER_DIR / ".env")


def _get(key: str, default: str = "") -> str:
    return os.environ.get(key) or _env.get(key) or default


def _get_float(key: str) -> float:
    raw = _get(key)
    try:
        return float(raw) if raw else 0.0
    except ValueError:
        return 0.0


class Config:
    host = _get("HOST", "0.0.0.0")
    port = int(_get("PORT", "8000"))
    pair_code = _get("PAIR_CODE", "884866")
    deepseek_base_url = _get("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")
    deepseek_api_key = _get("DEEPSEEK_API_KEY")
    deepseek_model = _get("DEEPSEEK_MODEL", "deepseek-v4-flash")
    price_prompt_per_m = _get_float("PRICE_PROMPT_PER_M")
    price_completion_per_m = _get_float("PRICE_COMPLETION_PER_M")
    data_dir = SERVER_DIR / "data"

    @property
    def deepseek_ready(self) -> bool:
        return bool(self.deepseek_api_key)


cfg = Config()
