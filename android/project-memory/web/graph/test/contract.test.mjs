import test from 'node:test';
import assert from 'node:assert/strict';
import { validateProjection } from '../src/contract.mjs';

const projectNode = {
  id: 'project:p', type: 'project', label: 'P',
  sourceUri: 'memory://project/p', sourceId: 'p'
};

test('accepts a bounded local projection', () => assert.ok(validateProjection({
  projectId: 'p', nodes: [projectNode], edges: []
})));

test('rejects dangling edges', () => assert.equal(validateProjection({
  projectId: 'p', nodes: [projectNode],
  edges: [{ id: 'edge', source: 'project:p', target: 'agent:x', type: 'contains', sourceUri: 'memory://project/p/relation/edge', sourceId: 'edge' }]
}), null));

test('rejects missing or foreign source evidence', () => assert.equal(validateProjection({
  projectId: 'p', nodes: [{ ...projectNode, sourceUri: 'https://example.com/leak' }], edges: []
}), null));

test('rejects oversized graphs', () => assert.equal(validateProjection({
  projectId: 'p',
  nodes: Array.from({ length: 101 }, (_, i) => ({ ...projectNode, id: `memory:${i}`, type: 'memory', sourceId: `${i}` })),
  edges: []
}), null));
