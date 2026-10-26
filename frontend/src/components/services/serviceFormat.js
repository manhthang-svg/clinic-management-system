const PRICE_FORMAT = new Intl.NumberFormat('vi-VN');

/** Price in VND with thousands separators, e.g. 150.000. */
export function formatPrice(value) {
  return PRICE_FORMAT.format(value);
}
