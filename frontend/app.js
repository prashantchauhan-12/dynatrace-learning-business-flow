// ============================================
// File Processing Pipeline — Frontend Logic
// ============================================

const API_BASE = 'http://localhost:3001';
const SERVICES = {
  upload: 'http://localhost:3001',
  processing: 'http://localhost:3002',
  validation: 'http://localhost:3003'
};

// ============================================
// DOM Elements
// ============================================

const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const selectedFile = document.getElementById('selectedFile');
const selectedFileName = document.getElementById('selectedFileName');
const selectedFileSize = document.getElementById('selectedFileSize');
const removeFileBtn = document.getElementById('removeFile');
const accountIdInput = document.getElementById('accountId');
const descriptionInput = document.getElementById('description');
const uploadBtn = document.getElementById('uploadBtn');
const statusFileIdInput = document.getElementById('statusFileId');
const checkStatusBtn = document.getElementById('checkStatusBtn');
const statusResult = document.getElementById('statusResult');
const statusGrid = document.getElementById('statusGrid');
const logContainer = document.getElementById('logContainer');
const clearLogBtn = document.getElementById('clearLogBtn');
const refreshHistoryBtn = document.getElementById('refreshHistoryBtn');
const historyBody = document.getElementById('historyBody');

let currentFile = null;

// ============================================
// Service Health Check
// ============================================

async function checkServiceHealth() {
  const checks = [
    { id: 'uploadStatus', url: `${SERVICES.upload}/health`, name: 'Upload' },
    { id: 'processingStatus', url: `${SERVICES.processing}/health`, name: 'Processing' },
    { id: 'validationStatus', url: `${SERVICES.validation}/health`, name: 'Validation' }
  ];

  for (const check of checks) {
    const dot = document.getElementById(check.id);
    try {
      const res = await fetch(check.url, { signal: AbortSignal.timeout(2000) });
      if (res.ok) {
        dot.className = 'status-dot online';
        dot.title = `${check.name} Service — Online`;
      } else {
        dot.className = 'status-dot offline';
        dot.title = `${check.name} Service — Error`;
      }
    } catch {
      dot.className = 'status-dot offline';
      dot.title = `${check.name} Service — Offline`;
    }
  }
}

// Check health every 10 seconds
checkServiceHealth();
setInterval(checkServiceHealth, 10000);

// ============================================
// File Selection
// ============================================

dropZone.addEventListener('click', () => fileInput.click());

dropZone.addEventListener('dragover', (e) => {
  e.preventDefault();
  dropZone.classList.add('drag-over');
});

dropZone.addEventListener('dragleave', () => {
  dropZone.classList.remove('drag-over');
});

dropZone.addEventListener('drop', (e) => {
  e.preventDefault();
  dropZone.classList.remove('drag-over');
  if (e.dataTransfer.files.length > 0) {
    selectFile(e.dataTransfer.files[0]);
  }
});

fileInput.addEventListener('change', () => {
  if (fileInput.files.length > 0) {
    selectFile(fileInput.files[0]);
  }
});

removeFileBtn.addEventListener('click', () => {
  clearFileSelection();
});

function selectFile(file) {
  currentFile = file;
  selectedFileName.textContent = file.name;
  selectedFileSize.textContent = formatBytes(file.size);
  selectedFile.style.display = 'flex';
  dropZone.style.display = 'none';
  uploadBtn.disabled = false;
}

function clearFileSelection() {
  currentFile = null;
  fileInput.value = '';
  selectedFile.style.display = 'none';
  dropZone.style.display = 'flex';
  uploadBtn.disabled = true;
}

// ============================================
// Upload
// ============================================

uploadBtn.addEventListener('click', async () => {
  if (!currentFile) return;

  const btnText = uploadBtn.querySelector('.btn-text');
  const btnLoader = uploadBtn.querySelector('.btn-loader');
  uploadBtn.disabled = true;
  btnText.textContent = 'Uploading...';
  btnLoader.style.display = 'inline-block';

  // Animate pipeline
  setPipelineState('upload');

  const formData = new FormData();
  formData.append('file', currentFile);
  formData.append('accountId', accountIdInput.value.trim());
  formData.append('description', descriptionInput.value.trim());

  try {
    addLog('info', '📤 Uploading file...', `File: ${currentFile.name}\nSize: ${formatBytes(currentFile.size)}`);

    const res = await fetch(`${API_BASE}/api/files/upload`, {
      method: 'POST',
      body: formData
    });

    const data = await res.json();

    if (res.ok) {
      addLog('success', '✅ File uploaded successfully', JSON.stringify(data, null, 2));
      showToast(`File uploaded: ${data.fileId}`, 'success');

      // Set file ID in status checker for convenience
      statusFileIdInput.value = data.fileId;

      // Animate pipeline through processing and validation
      setPipelineState('processing');
      addLog('processing', '⚙️ Processing initiated...', `File ID: ${data.fileId}`);

      // Poll for completion
      pollFileStatus(data.fileId);

    } else {
      addLog('error', '❌ Upload failed', JSON.stringify(data, null, 2));
      showToast('Upload failed', 'error');
      resetPipeline();
    }
  } catch (err) {
    addLog('error', '❌ Connection error', err.message);
    showToast('Connection error — is the Upload Service running?', 'error');
    resetPipeline();
  }

  // Reset button
  btnText.textContent = 'Upload & Process';
  btnLoader.style.display = 'none';
  clearFileSelection();
  accountIdInput.value = '';
  descriptionInput.value = '';
  refreshHistory();
});

// ============================================
// Poll File Status
// ============================================

async function pollFileStatus(fileId) {
  let attempts = 0;
  const maxAttempts = 15;
  const interval = 1000;

  const poll = setInterval(async () => {
    attempts++;
    try {
      const res = await fetch(`${API_BASE}/api/files/${fileId}`);
      const data = await res.json();

      if (data.overallStatus === 'completed' || data.overallStatus === 'completed_with_errors') {
        clearInterval(poll);
        setPipelineState('complete');
        addLog('success', '✅ Pipeline complete', JSON.stringify(data, null, 2));
        showToast(`Pipeline complete: ${data.overallStatus}`, 'success');
        refreshHistory();
      } else if (data.overallStatus === 'failed') {
        clearInterval(poll);
        setPipelineState('failed');
        addLog('error', '❌ Pipeline failed', JSON.stringify(data, null, 2));
        showToast('Pipeline failed', 'error');
        refreshHistory();
      } else if (data.processingStatus === 'completed') {
        setPipelineState('validation');
        addLog('processing', '🔍 Validation in progress...', `File ID: ${fileId}`);
      }

      if (attempts >= maxAttempts) {
        clearInterval(poll);
        addLog('warning', '⏳ Polling timeout', 'Check status manually using the file ID.');
        refreshHistory();
      }
    } catch {
      // Service might be busy, keep polling
    }
  }, interval);
}

// ============================================
// Status Check
// ============================================

checkStatusBtn.addEventListener('click', async () => {
  const fileId = statusFileIdInput.value.trim();
  if (!fileId) {
    showToast('Enter a File ID', 'error');
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/files/${fileId}`);
    const data = await res.json();

    if (res.ok) {
      statusResult.style.display = 'block';
      statusGrid.innerHTML = buildStatusHTML(data);
      addLog('info', `🔍 Status checked: ${fileId}`, JSON.stringify(data, null, 2));
    } else {
      statusResult.style.display = 'block';
      statusGrid.innerHTML = `<div class="status-row"><span class="label">Error</span><span class="value" style="color:var(--accent-red)">${data.error}</span></div>`;
    }
  } catch (err) {
    showToast('Failed to check status', 'error');
  }
});

function buildStatusHTML(data) {
  return `
    <div class="status-row">
      <span class="label">File ID</span>
      <span class="value" style="color:var(--accent-cyan)">${data.fileId}</span>
    </div>
    <div class="status-row">
      <span class="label">Account</span>
      <span class="value">${data.accountId || '—'}</span>
    </div>
    <div class="status-row">
      <span class="label">File Name</span>
      <span class="value">${data.fileName}</span>
    </div>
    <div class="status-row">
      <span class="label">Upload</span>
      ${getBadge(data.uploadStatus)}
    </div>
    <div class="status-row">
      <span class="label">Processing</span>
      ${getBadge(data.processingStatus)}
    </div>
    <div class="status-row">
      <span class="label">Validation</span>
      ${getBadge(data.validationStatus)}
    </div>
    <div class="status-row">
      <span class="label">Overall</span>
      ${getBadge(data.overallStatus)}
    </div>
  `;
}

function getBadge(status) {
  const map = {
    uploaded: ['success', '✓ Uploaded'],
    completed: ['success', '✓ Completed'],
    completed_with_errors: ['warning', '⚠ With Errors'],
    passed: ['success', '✓ Passed'],
    passed_with_warnings: ['warning', '⚠ Warnings'],
    failed: ['error', '✕ Failed'],
    pending: ['pending', '⏳ Pending'],
    in_progress: ['info', '⏳ In Progress'],
    skipped: ['pending', '— Skipped'],
    error: ['error', '✕ Error']
  };
  const [cls, label] = map[status] || ['info', status];
  return `<span class="badge badge-${cls}">${label}</span>`;
}

// ============================================
// Pipeline Animation
// ============================================

function setPipelineState(state) {
  const steps = [
    document.getElementById('pipelineStep1'),
    document.getElementById('pipelineStep2'),
    document.getElementById('pipelineStep3')
  ];

  // Reset all
  steps.forEach(s => s.className = 'pipeline-step');

  switch (state) {
    case 'upload':
      steps[0].classList.add('active');
      break;
    case 'processing':
      steps[0].classList.add('completed');
      steps[1].classList.add('active');
      break;
    case 'validation':
      steps[0].classList.add('completed');
      steps[1].classList.add('completed');
      steps[2].classList.add('active');
      break;
    case 'complete':
      steps[0].classList.add('completed');
      steps[1].classList.add('completed');
      steps[2].classList.add('completed');
      break;
    case 'failed':
      steps[0].classList.add('completed');
      steps[1].classList.add('failed');
      steps[2].classList.add('failed');
      break;
  }
}

function resetPipeline() {
  const steps = [
    document.getElementById('pipelineStep1'),
    document.getElementById('pipelineStep2'),
    document.getElementById('pipelineStep3')
  ];
  steps.forEach(s => s.className = 'pipeline-step');
}

// ============================================
// Activity Log
// ============================================

function addLog(type, title, body) {
  // Remove empty message
  const empty = logContainer.querySelector('.log-empty');
  if (empty) empty.remove();

  const entry = document.createElement('div');
  entry.className = `log-entry ${type}`;
  entry.innerHTML = `
    <div class="log-entry-header">
      <span class="log-entry-title">${title}</span>
      <span class="log-entry-time">${new Date().toLocaleTimeString()}</span>
    </div>
    <div class="log-entry-body">${escapeHtml(body)}</div>
  `;

  logContainer.insertBefore(entry, logContainer.firstChild);
}

clearLogBtn.addEventListener('click', () => {
  logContainer.innerHTML = '<div class="log-empty">No activity yet. Upload a file to get started.</div>';
});

// ============================================
// File History
// ============================================

async function refreshHistory() {
  try {
    const res = await fetch(`${API_BASE}/api/files`);
    const data = await res.json();

    if (data.files && data.files.length > 0) {
      historyBody.innerHTML = data.files.map(f => `
        <tr>
          <td><span class="file-id" onclick="document.getElementById('statusFileId').value='${f.fileId}'; document.getElementById('checkStatusBtn').click();">${f.fileId}</span></td>
          <td>${f.accountId}</td>
          <td>${f.fileName}</td>
          <td>${formatBytes(f.fileSize)}</td>
          <td>${getBadge(f.overallStatus)}</td>
          <td>${new Date(f.uploadedAt).toLocaleString()}</td>
        </tr>
      `).join('');
    } else {
      historyBody.innerHTML = '<tr class="empty-row"><td colspan="6">No files uploaded yet</td></tr>';
    }
  } catch {
    // Service might be offline
  }
}

refreshHistoryBtn.addEventListener('click', refreshHistory);

// Load history on page load
refreshHistory();

// ============================================
// Helpers
// ============================================

function formatBytes(bytes) {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// ============================================
// Toast Notifications
// ============================================

let toastContainer = document.querySelector('.toast-container');
if (!toastContainer) {
  toastContainer = document.createElement('div');
  toastContainer.className = 'toast-container';
  document.body.appendChild(toastContainer);
}

function showToast(message, type = 'info') {
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️';
  toast.textContent = `${icon} ${message}`;
  toastContainer.appendChild(toast);
  setTimeout(() => toast.remove(), 4000);
}
