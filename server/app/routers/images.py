"""日志图片：上传保存到 data/images/，按 id 访问。"""
import re
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, UploadFile
from fastapi.responses import FileResponse

from ..config import cfg
from ..deps import require_token

router = APIRouter(prefix="/api/v1", tags=["images"], dependencies=[Depends(require_token)])

_EXT = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp", "image/gif": ".gif"}


@router.post("/images")
async def upload_image(file: UploadFile):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="只支持图片文件")
    ext = _EXT.get(file.content_type)
    if not ext and file.filename and "." in file.filename:
        ext = "." + file.filename.rsplit(".", 1)[-1].lower()
    image_id = uuid4().hex
    (cfg.data_dir / "images" / f"{image_id}{ext or '.jpg'}").write_bytes(await file.read())
    return {"id": image_id, "url": f"/api/v1/images/{image_id}"}


@router.get("/images/{image_id}")
def get_image(image_id: str):
    if not re.fullmatch(r"[0-9a-f]{32}", image_id):
        raise HTTPException(status_code=400, detail="非法图片 ID")
    matches = list((cfg.data_dir / "images").glob(f"{image_id}.*"))
    if not matches:
        raise HTTPException(status_code=404, detail="图片不存在")
    return FileResponse(matches[0])
