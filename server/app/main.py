"""TimeMaster 服务端入口。"""
import socket
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from . import db
from .config import cfg
from .routers import agent, data, images, pair

VERSION = "0.1.0"


def _lan_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except OSError:
        return "127.0.0.1"


@asynccontextmanager
async def lifespan(_app: FastAPI):
    db.init_db()
    print("=" * 56)
    print(f"TimeMaster 服务端 v{VERSION} 已启动")
    print(f"本机局域网地址（App 里填这个）: http://{_lan_ip()}:{cfg.port}")
    print(f"配对码: {cfg.pair_code}")
    print("=" * 56, flush=True)
    yield


app = FastAPI(title="TimeMaster Server", version=VERSION, lifespan=lifespan)
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"]
)

for r in (pair.router, data.router, images.router, agent.router):
    app.include_router(r)


@app.get("/api/v1/health")
def health():
    return {"status": "ok", "version": VERSION, "deepseek_ready": cfg.deepseek_ready}
