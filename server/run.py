"""服务端启动入口：python3 run.py"""
import uvicorn

from app.config import cfg
from app.main import app

if __name__ == "__main__":
    uvicorn.run(app, host=cfg.host, port=cfg.port)
