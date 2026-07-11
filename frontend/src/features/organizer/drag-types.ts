export const PIXIERGE_ASSET_DRAG_MIME = 'application/x-pixierge-assets';

export type AssetDragPayload = {
  assetIds: string[];
  items: Array<{ assetId: string; sourceLibraryId: string }>;
};

export function writeAssetDragData(dataTransfer: DataTransfer, payload: AssetDragPayload) {
  dataTransfer.setData(PIXIERGE_ASSET_DRAG_MIME, JSON.stringify(payload));
  dataTransfer.setData('text/plain', `${payload.assetIds.length} photo${payload.assetIds.length === 1 ? '' : 's'}`);
  dataTransfer.effectAllowed = 'copy';
}

export function readAssetDragData(dataTransfer: DataTransfer): AssetDragPayload | null {
  const raw = dataTransfer.getData(PIXIERGE_ASSET_DRAG_MIME);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as AssetDragPayload;
    if (!Array.isArray(parsed.assetIds) || !Array.isArray(parsed.items)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}
