# Agent UI Editor — User guide (how to use the UI)

This guide explains how to use the Agent UI Editor: the **Workflows list**, the **Editor** (canvas and side panel), and **Run**.

---

## 1. Opening the app

After you start the backend (e.g. `./gradlew :be:bootRun`), open the app in your browser at **http://localhost:8085** (or the port in your config).

You will see either:

- **Workflows list** — if you go to the home page (`/`), or
- **Workflow editor** — if you open a workflow or create a new one.

---

## 2. Workflows list

The list shows all saved workflows.

| What you see | What it does |
|--------------|----------------|
| **Workflows** | Page title. |
| **Create workflow** | Link to open the editor for a **new** workflow (no name, empty canvas). |
| **Load example:** dropdown | Lists example workflows (Story, Evening plan, Expert router, Supervisor) seeded at startup. Choosing one opens that workflow in the editor so you can view and edit its graph. |
| Each row: **name**, **date**, **Run**, **Delete** | **Name** opens the editor for that workflow. **Run** opens the Run dialog. **Delete** asks for confirmation and then deletes the workflow from the server. |

**To create a workflow:** Click **Create workflow**. You are taken to the editor with an empty canvas.

**To edit a workflow:** Click the workflow **name**. You are taken to the editor with that workflow loaded.

**To run a workflow:** Click **Run** on a row. The Run dialog opens so you can send input and see the result.

**To delete a workflow:** Click **Delete** on a row. Confirm in the dialog. The workflow is removed and the list refreshes.

---

## 3. Workflow editor — layout (n8n-style)

The editor uses a **three-column layout** similar to n8n:

1. **Top bar**
   - **Back** — return to the Workflows list.
   - **Workflow name** — editable name; click **Save** to persist.
   - **Entry: &lt;id&gt;** — which node is the entry (set in the **right** panel when a node is selected).
   - **Layout** — arranges the graph in an orchestrator-style flow. If an **entry node** is set: entry is on the **left**, the main execution flow goes to the **right**, and dependency nodes (LLM/router/tool) are attached **below** their parent nodes with dashed links. If no entry is set: nodes are arranged left-to-right by dependency depth.
   - **Save**, **Run**, **Delete** — save the workflow, run it, or delete it (Run/Delete only for saved workflows).

2. **Left panel — Add step + Agents**
   - A vertical list of node types: **LLM**, **Agent**, **Supervisor**, **Sequence**, **Parallel**, **Conditional** (each with a dedicated icon).
   - Click a type to add that node to the **center** canvas.
   - Includes an **Agents** list (agent + supervisor nodes already in the workflow). Click one to focus/select it on the canvas.

3. **Center — Canvas**
   - The flow graph: nodes and edges.
   - **Pan:** drag the background. **Zoom:** +/- controls (bottom-left) or scroll.
   - **Select:** click a node; its settings appear in the **right** panel.
   - **Connect:** drag from a node’s handle (small dot) to another node’s handle.
   - **Delete:** select a node and press **Delete** or **Backspace**, or use **Delete node** in the right panel.

4. **Right panel — Node configuration**
   - When **no node** is selected: shows “Click a node on the canvas to configure it.”
   - When a **node is selected**: shows **Node configuration** (ID, type-specific fields), **Dependencies** (Uses / Used by, like a DSL), **Set as entry**, **Delete node**.

5. **Dependencies and edges**
   - **Edges** on the canvas show relationship labels (e.g. “model”, “delegates”, “router”, “tool”, “branch”) so the graph reads like a DSL.
   - Main orchestration links use side handles; dependency links use top/bottom handles.
   - In the right panel, **Dependencies** for the selected node lists **Uses** (nodes this one depends on) and **Used by** (nodes that depend on this one).

---

## 4. Editor — step-by-step

### 4.1 Add nodes

- In the left panel, under **Add node**, click a type: **LLM**, **Agent**, **Supervisor**, **Sequence**, **Parallel**, **Conditional**.
- A new node appears on the canvas. You can drag it to position it.
- When you add an **Agent** or **Supervisor**, the editor also auto-creates a dedicated **LLM** node and links it (`llmId`) as a pair.

**Rough meaning of node types:**

- **LLM** — language model config (base URL, model name). Other nodes that use an LLM should connect to this node.
- **Agent** — single agent using an LLM (and optional tools).
- **Supervisor** — chooses one of several sub-agents.
- **Sequence** — runs sub-agents one after another.
- **Parallel** — runs sub-agents in parallel.
- **Conditional** — routes to different agents based on a condition (uses a router agent and branches).

### 4.2 Connect nodes (edges)

- Drag from a **source** node’s handle (small circle on the edge) to a **target** node’s handle.
- Edges define the graph: e.g. **LLM → Agent** (agent uses that LLM), **Agent → Sequence** (agent is a step in the sequence).

### 4.3 Configure a node

- **Click** a node on the canvas to select it.
- The **right panel** shows **Node configuration** with:
  - **ID** — unique identifier (used in connections and as entry).
  - **Type-specific fields** — e.g. for LLM: Base URL, Model, Temperature, Max tokens; for Agent: Name, LLM ID, Output key, Tool IDs, plus:
    - **Role** — short role label for that agent.
    - **System message** — system instruction for that specific agent/sub-agent.
    - **Prompt template** — per-agent prompt template. Supports placeholders like `{{metadata.prompt}}`, `{{metadata.topic}}`, `{{metadata.style}}`.
- **Set as entry** — mark this node as the one that runs when you click **Run**. Exactly one node should be the entry (usually a Sequence or Supervisor; `parallel` is often better as an inner step).
- **Set as entry** — only **Sequence**, **Parallel**, or **Supervisor** nodes can be marked as entry.
- **Delete node** — remove this node from the canvas (and any edges to/from it). You can also select the node and press **Delete** or **Backspace**.

### 4.4 Save

- Give the workflow a **name** in the top bar and set an **entry** node.
- Click **Save**.
  - If this is a new workflow, it is created and the URL changes to `/workflows/&lt;id&gt;`.
  - If you opened an existing workflow, it is updated.
- You must have at least one node and an entry node; otherwise Save will show an error.

### 4.5 Delete workflow (from the editor)

- For a **saved** workflow, the top bar shows **Delete workflow**.
- Click it, confirm in the dialog. The workflow is deleted and you are returned to the Workflows list.

### 4.6 Delete a node

- **From the canvas:** Select the node (click it), then press **Delete** or **Backspace**. Connected edges are removed. If the entry node was deleted, the entry is cleared.
- **From the right panel:** With the node selected, click **Delete node** in the Node configuration panel and confirm.

---

## 5. Run workflow

**From the list:** Click **Run** on a workflow row.

**From the editor:** Open a saved workflow and click **Run** in the top bar.

The **Run** dialog opens:

1. **Input (JSON)** — a text area with a JSON object, e.g. `{ "metadata": { "prompt": "Hello", "topic": "test" } }`. Edit it to match what your workflow expects (e.g. `metadata.topic`, `metadata.style`, `metadata.mood`).
2. Click **Run**. The app calls the run API and shows the **Result** (or an error).
3. The dialog also shows **Executed nodes** from run trace metadata.
4. In the editor view, nodes executed in the last run are highlighted on the canvas.
5. **Close** — closes the dialog.

If the backend or LLM returns an error, it is shown in the dialog instead of a result.

---

## 6. Quick reference

| Goal | Action |
|------|--------|
| Create a new workflow | List → **Create workflow** → add nodes, connect, set entry → **Save** |
| Edit a workflow | List → click workflow **name** → change nodes/edges/fields → **Save** |
| Run a workflow | List → **Run** on a row, or Editor → **Run** (saved only) |
| Delete a workflow | List → **Delete** on a row (confirm), or Editor → **Delete workflow** (confirm) |
| Add a node | Editor → left panel **Add node** → click type (LLM, Agent, …) |
| Connect two nodes | Editor → drag from one node’s handle to another’s |
| Set entry node | Editor → select node → right panel **Set as entry** |
| Edit node fields | Editor → select node → right panel (ID, type-specific fields) |
| Delete a node | Editor → select node → press **Delete**/ **Backspace**, or right panel **Delete node** |
| Go back to list | Editor → **← Back** |

---

## 7. Tips

- **Entry node:** Usually the entry is a **Sequence** or **Supervisor** that then uses agents and LLMs. `Parallel` can be entry, but results are usually clearer when parallel work is followed by a composing agent.
- **LLM ID:** For Agent/Supervisor nodes, the **LLM ID** field should match the **ID** of an LLM node. You can also connect the LLM node to the Agent with an edge; the converter may derive the LLM from the edge.
- **Save often:** Changes on the canvas are only in the browser until you click **Save**. After saving, **Run** and **Delete workflow** are available for that workflow.
- **Errors:** If Save or Run fails, the message is shown in the top bar (editor) or in the Run dialog. Fix the graph or input and try again.
