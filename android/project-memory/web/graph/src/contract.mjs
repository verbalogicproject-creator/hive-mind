export const NODE_LIMIT = 100;
export const EDGE_LIMIT = 150;
const TYPES = new Set(['project', 'agent', 'session', 'memory']);
const text = (value, limit = 512) => typeof value === 'string' && value.length > 0 && value.length <= limit;
const localSource = value => text(value, 2048) && value.startsWith('memory://project/');

export function validateProjection(value) {
  if (!value || typeof value !== 'object' || !text(value.projectId, 256)) return null;
  if (!Array.isArray(value.nodes) || !Array.isArray(value.edges)) return null;
  if (value.nodes.length > NODE_LIMIT || value.edges.length > EDGE_LIMIT) return null;
  const ids = new Set();
  for (const node of value.nodes) {
    if (!node || !text(node.id, 256) || !text(node.label) || !TYPES.has(node.type)) return null;
    if (!text(node.sourceId, 256) || !localSource(node.sourceUri) || ids.has(node.id)) return null;
    ids.add(node.id);
  }
  for (const edge of value.edges) {
    if (!edge || !text(edge.id, 512) || !text(edge.type, 128)) return null;
    if (!ids.has(edge.source) || !ids.has(edge.target)) return null;
    if (!text(edge.sourceId, 256) || !localSource(edge.sourceUri)) return null;
  }
  return value;
}
