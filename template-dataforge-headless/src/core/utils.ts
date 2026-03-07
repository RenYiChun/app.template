/**
 * 通用路径拼接工具
 */
export function joinPath(...parts: (string | number | undefined)[]): string {
    const result = parts
        .filter((p) => p !== undefined && p !== null && p !== '')
        .map((p) => String(p).replaceAll(/\/+/g, '/').replace(/^\//, '').replace(/\/$/, ''))
        .join('/');

    const first = parts.find(Boolean);
    if (first && String(first).startsWith('/')) {
        return `/${result}`;
    }
    return result;
}

export function ensureSlash(s: string): string {
    if (!s || s === '/') return '';
    return s.startsWith('/') ? s : `/${s}`;
}
