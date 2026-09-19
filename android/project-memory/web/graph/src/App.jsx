import React, { useEffect, useMemo, useState } from 'react';
import { Background, Controls, ReactFlow } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { validateProjection } from './contract.mjs';

const kinds = ['all', 'project', 'agent', 'session', 'memory'];

export default function App() {
  const [projection, setProjection] = useState({ nodes: [], edges: [], truncated: false });
  const [type, setType] = useState('all');
  const [agentId, setAgentId] = useState('all');

  useEffect(() => {
    const receive = event => {
      try {
        const valid = validateProjection(typeof event.data === 'string' ? JSON.parse(event.data) : event.data);
        if (valid) setProjection(valid);
      } catch {
        // Native code supplies the recovery surface; malformed documents are ignored here.
      }
    };
    window.addEventListener('message', receive);
    return () => window.removeEventListener('message', receive);
  }, []);

  const agents = useMemo(() => projection.nodes.filter(node => node.type === 'agent'), [projection]);
  const agentScope = useMemo(() => {
    if (agentId === 'all') return null;
    const ids = new Set(projection.nodes.filter(node => node.type === 'project').map(node => node.id));
    ids.add(agentId);
    let changed = true;
    while (changed) {
      changed = false;
      for (const edge of projection.edges) {
        if (ids.has(edge.source) && !ids.has(edge.target)) {
          ids.add(edge.target);
          changed = true;
        }
      }
    }
    return ids;
  }, [projection, agentId]);
  const visible = useMemo(() => projection.nodes.filter(node =>
    (agentScope === null || agentScope.has(node.id)) && (type === 'all' || node.type === type)
  ), [projection, type, agentScope]);
  const ids = useMemo(() => new Set(visible.map(node => node.id)), [visible]);
  const nodes = visible.map((node, index) => ({
    id: node.id,
    data: { label: node.label },
    position: { x: (index % 3) * 220, y: Math.floor(index / 3) * 120 },
    className: `node-${node.type}`,
    draggable: false,
    selectable: true
  }));
  const edges = projection.edges
    .filter(edge => ids.has(edge.source) && ids.has(edge.target))
    .map(edge => ({ ...edge, label: edge.type, selectable: false }));

  return <main>
    <nav aria-label="Graph filters">
      {kinds.map(value => <button key={value} aria-pressed={type === value} onClick={() => setType(value)}>{value}</button>)}
      <label>Agent<select value={agentId} onChange={event => setAgentId(event.target.value)}>
        <option value="all">All agents</option>
        {agents.map(agent => <option key={agent.id} value={agent.id}>{agent.label}</option>)}
      </select></label>
    </nav>
    <p role="status">{projection.truncated ? 'Newest evidence shown · graph truncated' : `${visible.length} items · offline`}</p>
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodesDraggable={false}
      nodesConnectable={false}
      edgesFocusable={false}
      onNodeClick={(_, node) => window.ProjectMemory?.postMessage(JSON.stringify({ type: 'node-selected', id: node.id }))}
      fitView
    >
      <Background />
      <Controls showInteractive={false} />
    </ReactFlow>
  </main>;
}
