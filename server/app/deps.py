"""鉴权：配对成功后发放 token，业务接口统一校验 Bearer token。"""
from fastapi import Header, HTTPException

from . import db


def require_token(authorization: str | None = Header(default=None)) -> None:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少 token，请先在设置里完成配对")
    token = authorization.removeprefix("Bearer ").strip()
    if not token or token != db.get_setting("paired_token"):
        raise HTTPException(status_code=401, detail="token 无效，请重新配对")
