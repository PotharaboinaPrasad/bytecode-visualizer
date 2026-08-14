// bcviz frontend - vanilla JS, no build step, no framework.

let currentClass = null;
let currentMethod = null;
let currentStep = 0;

const OP_CATEGORY = {
  // constants
  const: ['bipush','sipush','ldc','ldc_w','ldc2_w','aconst_null',
          'iconst_m1','iconst_0','iconst_1','iconst_2','iconst_3','iconst_4','iconst_5',
          'lconst_0','lconst_1','fconst_0','fconst_1','fconst_2','dconst_0','dconst_1'],
  local: ['iload','lload','fload','dload','aload','istore','lstore','fstore','dstore','astore','iinc',
          'iload_0','iload_1','iload_2','iload_3','istore_0','istore_1','istore_2','istore_3'],
  arith: ['iadd','ladd','fadd','dadd','isub','lsub','fsub','dsub','imul','lmul','fmul','dmul',
          'idiv','ldiv','fdiv','ddiv','irem','lrem','frem','drem','ineg','lneg','fneg','dneg',
          'ishl','lshl','ishr','lshr','iushr','lushr','iand','land','ior','lor','ixor','lxor'],
  branch: ['ifeq','ifne','iflt','ifge','ifgt','ifle','if_icmpeq','if_icmpne','if_icmplt','if_icmpge',
           'if_icmpgt','if_icmple','if_acmpeq','if_acmpne','goto','ifnull','ifnonnull',
           'ireturn','lreturn','freturn','dreturn','areturn','return'],
  call: ['invokevirtual','invokespecial','invokestatic','invokeinterface','invokedynamic',
         'getfield','putfield','getstatic','putstatic','new'],
  stack: ['dup','dup_x1','dup_x2','dup2','dup2_x1','dup2_x2','pop','pop2','swap'],
};

function categoryOf(opcode) {
  for (const [cat, list] of Object.entries(OP_CATEGORY)) {
    if (list.includes(opcode)) return cat;
  }
  return null;
}

async function loadClasses() {
  const res = await fetch('/api/classes');
  const classes = await res.json();
  const list = document.getElementById('classList');
  list.innerHTML = '';
  classes.forEach(name => {
    const li = document.createElement('li');
    li.textContent = name;
    li.onclick = () => selectClass(name, li);
    list.appendChild(li);
  });
  if (classes.length > 0) selectClass(classes[0], list.firstChild);
}

async function selectClass(name, liEl) {
  document.querySelectorAll('#classList li').forEach(li => li.classList.remove('active'));
  if (liEl) liEl.classList.add('active');

  const res = await fetch('/api/analyze?file=' + encodeURIComponent(name));
  currentClass = await res.json();
  document.getElementById('classMeta').textContent =
    currentClass.name + '  ·  extends ' + (currentClass.superName || 'Object');

  const methodList = document.getElementById('methodList');
  methodList.innerHTML = '';
  currentClass.methods.forEach((m, idx) => {
    const li = document.createElement('li');
    li.innerHTML = `<span class="m-name">${m.name}</span><span class="m-sig">${escapeHtml(m.signature)}</span>`;
    li.onclick = () => selectMethod(idx, li);
    methodList.appendChild(li);
  });
  if (currentClass.methods.length > 0) selectMethod(0, methodList.firstChild);
}

function selectMethod(idx, liEl) {
  document.querySelectorAll('#methodList li').forEach(li => li.classList.remove('active'));
  if (liEl) liEl.classList.add('active');

  currentMethod = currentClass.methods[idx];
  currentStep = 0;

  document.getElementById('methodHeader').innerHTML =
    `<span class="sig">${escapeHtml(currentMethod.signature)}</span> <span style="color:var(--muted)">— ${currentMethod.access}</span>`;

  renderInstructionTable();
  goToStep(firstRealInstrIndex());
}

function firstRealInstrIndex() {
  const i = currentMethod.instructions.findIndex(x => !x.isLabel);
  return i >= 0 ? i : 0;
}

function renderInstructionTable() {
  const body = document.getElementById('instrBody');
  body.innerHTML = '';
  currentMethod.instructions.forEach((instr, i) => {
    const tr = document.createElement('tr');
    if (instr.isLabel) {
      tr.className = 'label-row';
      tr.innerHTML = `<td></td><td></td><td colspan="3">${escapeHtml(instr.opcode)}</td>`;
    } else {
      tr.className = 'instr-row';
      tr.dataset.stepIndex = i;
      const cat = categoryOf(instr.opcode);
      const opClass = cat ? 'op-' + cat : '';
      tr.innerHTML = `
        <td class="col-idx">${instr.index}</td>
        <td class="col-line">${instr.line !== null ? instr.line : ''}</td>
        <td><span class="op-tag ${opClass}">${escapeHtml(instr.opcode)}</span></td>
        <td class="col-operand">${escapeHtml(instr.operand)}</td>
        <td class="col-note">${escapeHtml(instr.note)}</td>`;
      tr.onclick = () => goToStep(i);
    }
    body.appendChild(tr);
  });
}

function goToStep(stepIndex) {
  currentStep = stepIndex;
  const instr = currentMethod.instructions[stepIndex];
  if (!instr) return;

  document.querySelectorAll('#instrBody tr.instr-row').forEach(tr => tr.classList.remove('selected'));
  const row = document.querySelector(`#instrBody tr[data-step-index="${stepIndex}"]`);
  if (row) {
    row.classList.add('selected');
    row.scrollIntoView({ block: 'nearest' });
  }

  renderStack(instr.stack);
  document.getElementById('noteBox').textContent =
    (instr.note || '') + (instr.description ? '  —  ' + instr.description : '');
  document.getElementById('stepIndicator').textContent =
    `(instr ${instr.index !== null ? instr.index : '-'})`;

  updateStepButtons();
}

function renderStack(stackValues) {
  const visual = document.getElementById('stackVisual');
  // clear everything except the floor label
  visual.querySelectorAll('.stack-cell').forEach(el => el.remove());
  stackValues.forEach((val, i) => {
    const div = document.createElement('div');
    div.className = 'stack-cell' + (i === stackValues.length - 1 ? ' top' : '');
    div.textContent = val;
    visual.appendChild(div);
  });
}

function updateStepButtons() {
  const realIndices = currentMethod.instructions
    .map((x, i) => x.isLabel ? null : i)
    .filter(x => x !== null);
  const pos = realIndices.indexOf(currentStep);
  document.getElementById('prevStep').disabled = pos <= 0;
  document.getElementById('nextStep').disabled = pos < 0 || pos >= realIndices.length - 1;

  document.getElementById('prevStep').onclick = () => {
    if (pos > 0) goToStep(realIndices[pos - 1]);
  };
  document.getElementById('nextStep').onclick = () => {
    if (pos < realIndices.length - 1) goToStep(realIndices[pos + 1]);
  };
}

function escapeHtml(s) {
  if (s === null || s === undefined) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

loadClasses();

// ---------- upload / paste code ----------
document.getElementById('fileInput').addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const text = await file.text();
  document.getElementById('codeInput').value = text;
});

document.getElementById('analyzeBtn').addEventListener('click', async () => {
  const source = document.getElementById('codeInput').value;
  const status = document.getElementById('uploadStatus');
  if (!source.trim()) {
    status.textContent = 'paste or choose a .java file first';
    status.className = 'upload-status error';
    return;
  }
  status.textContent = 'compiling…';
  status.className = 'upload-status';

  try {
    const res = await fetch('/api/upload', { method: 'POST', body: source });
    const data = await res.json();
    if (!res.ok) {
      status.textContent = data.error || 'compile failed';
      status.className = 'upload-status error';
      return;
    }
    status.textContent = 'compiled ' + data.className + ' ✓';
    status.className = 'upload-status ok';
    await loadClasses();
    // auto-select the newly uploaded class
    const items = document.querySelectorAll('#classList li');
    for (const li of items) {
      if (li.textContent === data.className) { li.click(); break; }
    }
  } catch (err) {
    status.textContent = 'network error: ' + err.message;
    status.className = 'upload-status error';
  }
});
