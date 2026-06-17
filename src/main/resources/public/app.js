import React, { useEffect, useMemo, useRef, useState } from "https://esm.sh/react@18.3.1";
import { createRoot } from "https://esm.sh/react-dom@18.3.1/client";
import htm from "https://esm.sh/htm@3.1.1";

const html = htm.bind(React.createElement);
const API_BASE = "/v1/files";

function formatBytes(bytes) {
  if (bytes === 0) {
    return "0 B";
  }

  const units = ["B", "KB", "MB", "GB"];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / (1024 ** exponent);
  return `${value.toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`;
}

function formatTimestamp(value) {
  if (!value) {
    return "Pending";
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function inferFileType(file) {
  return file.type || "application/octet-stream";
}

async function createUpload(file) {
  const response = await fetch(`${API_BASE}/actions/createUpload`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      fileName: file.name,
      fileSize: file.size,
      fileType: inferFileType(file)
    })
  });

  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.message || "Failed to create upload");
  }

  return {
    location: response.headers.get("Location") || `${API_BASE}/${payload.fileId}`,
    metadata: payload
  };
}

function uploadBytes(location, file, onProgress) {
  return new Promise((resolve, reject) => {
    const url = new URL(location, window.location.origin);
    url.searchParams.set("offset", "0");

    const request = new XMLHttpRequest();
    request.open("PUT", url.toString());
    request.setRequestHeader("Content-Type", "application/octet-stream");

    request.upload.addEventListener("progress", (event) => {
      if (!event.lengthComputable) {
        return;
      }

      onProgress(Math.round((event.loaded / event.total) * 100));
    });

    request.addEventListener("load", () => {
      try {
        const payload = request.responseText ? JSON.parse(request.responseText) : {};
        if (request.status >= 200 && request.status < 300) {
          resolve(payload);
          return;
        }

        const message = payload?.message
          || payload?._embedded?.errors?.[0]?.message
          || `Upload failed with status ${request.status}`;
        reject(new Error(message));
      } catch (error) {
        reject(new Error("Upload completed with an unreadable response"));
      }
    });

    request.addEventListener("error", () => reject(new Error("Network error while uploading bytes")));
    request.send(file);
  });
}

function UploadCard({ upload }) {
  return html`
    <article className="upload-card">
      <div className="upload-card__header">
        <div>
          <h3>${upload.fileName}</h3>
          <p>${formatBytes(upload.fileSize)} · ${upload.fileType}</p>
        </div>
        <span className=${`status-pill status-pill--${upload.variant}`}>${upload.status}</span>
      </div>
      <div className="progress-rail" aria-hidden="true">
        <div className="progress-rail__fill" style=${{ width: `${upload.progress}%` }}></div>
      </div>
      <div className="upload-card__meta">
        <span>${upload.progress}%</span>
        <span>${upload.fileId ? `File ID ${upload.fileId}` : "Awaiting file record"}</span>
      </div>
      ${upload.error ? html`<p className="upload-card__error">${upload.error}</p>` : null}
    </article>
  `;
}

function StoredFileTable({ files }) {
  if (files.length === 0) {
    return html`<div className="empty-state">No files uploaded yet.</div>`;
  }

  return html`
    <div className="table-shell">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Status</th>
            <th>Stored</th>
            <th>Created</th>
            <th>Download</th>
          </tr>
        </thead>
        <tbody>
          ${files.map((file) => html`
            <tr key=${file.fileId}>
              <td>
                <strong>${file.fileName}</strong>
                <span>${formatBytes(file.fileSize)} · ${file.fileType}</span>
              </td>
              <td>${file.status}</td>
              <td>${formatBytes(file.storedSize)}</td>
              <td>${formatTimestamp(file.createdAt)}</td>
              <td>
                <a href=${`${API_BASE}/${file.fileId}`}>Download</a>
              </td>
            </tr>
          `)}
        </tbody>
      </table>
    </div>
  `;
}

function App() {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const [uploads, setUploads] = useState([]);
  const [storedFiles, setStoredFiles] = useState([]);
  const [loadError, setLoadError] = useState("");

  const activeUploads = useMemo(
    () => uploads.filter((upload) => upload.variant === "working").length,
    [uploads]
  );

  useEffect(() => {
    void refreshFiles();
  }, []);

  async function refreshFiles() {
    try {
      const response = await fetch(API_BASE);
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload.message || "Failed to load files");
      }
      setStoredFiles(payload);
      setLoadError("");
    } catch (error) {
      setLoadError(error.message);
    }
  }

  function patchUpload(localId, patch) {
    setUploads((current) =>
      current.map((upload) => (upload.localId === localId ? { ...upload, ...patch } : upload))
    );
  }

  function queueFiles(fileList) {
    const files = Array.from(fileList || []);
    if (files.length === 0) {
      return;
    }

    const nextUploads = files.map((file) => ({
      localId: crypto.randomUUID(),
      file,
      fileName: file.name,
      fileSize: file.size,
      fileType: inferFileType(file),
      fileId: "",
      progress: 0,
      status: "Queued",
      variant: "queued",
      error: ""
    }));

    setUploads((current) => [...nextUploads, ...current]);
    nextUploads.forEach((upload) => {
      void processUpload(upload);
    });
  }

  async function processUpload(upload) {
    try {
      patchUpload(upload.localId, {
        status: "Creating upload",
        variant: "working",
        progress: 5
      });

      const { location, metadata } = await createUpload(upload.file);
      patchUpload(upload.localId, {
        status: "Uploading bytes",
        variant: "working",
        progress: 10,
        fileId: metadata.fileId
      });

      const uploaded = await uploadBytes(location, upload.file, (progress) => {
        patchUpload(upload.localId, {
          progress: Math.max(10, progress),
          status: `Uploading bytes ${progress}%`
        });
      });

      patchUpload(upload.localId, {
        status: uploaded.status || "Complete",
        variant: "complete",
        progress: 100,
        fileId: uploaded.fileId || metadata.fileId
      });

      await refreshFiles();
    } catch (error) {
      patchUpload(upload.localId, {
        status: "Upload failed",
        variant: "failed",
        error: error.message,
        progress: 0
      });
    }
  }

  function onDrop(event) {
    event.preventDefault();
    setDragging(false);
    queueFiles(event.dataTransfer.files);
  }

  return html`
    <main className="app-shell">
      <section className="hero">
        <div className="hero__copy">
          <p className="eyebrow">Local Object Storage</p>
          <h1>Drop files here and push them through the upload APIs.</h1>
          <p className="hero__body">
            This client calls <code>createUpload</code>, follows the returned
            <code>Location</code> header, and streams the file bytes into
            <code>PUT /v1/files/{fileId}</code>.
          </p>
        </div>
        <div className="hero__stats">
          <div>
            <strong>${storedFiles.length}</strong>
            <span>stored files</span>
          </div>
          <div>
            <strong>${activeUploads}</strong>
            <span>active uploads</span>
          </div>
        </div>
      </section>

      <section
        className=${`dropzone ${dragging ? "dropzone--active" : ""}`}
        onDragEnter=${(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragOver=${(event) => event.preventDefault()}
        onDragLeave=${(event) => {
          event.preventDefault();
          if (event.currentTarget.contains(event.relatedTarget)) {
            return;
          }
          setDragging(false);
        }}
        onDrop=${onDrop}
      >
        <input
          ref=${inputRef}
          className="sr-only"
          type="file"
          multiple
          onChange=${(event) => queueFiles(event.target.files)}
        />
        <p className="dropzone__label">Drag files here</p>
        <p className="dropzone__hint">or choose files from disk and upload them directly into the API</p>
        <button type="button" onClick=${() => inputRef.current?.click()}>
          Choose files
        </button>
      </section>

      <section className="panel">
        <div className="panel__header">
          <h2>Upload queue</h2>
          <button type="button" className="ghost-button" onClick=${() => setUploads([])}>
            Clear finished
          </button>
        </div>
        ${uploads.length === 0
          ? html`<div className="empty-state">Drop a file to create an upload record and send its bytes.</div>`
          : html`<div className="upload-grid">${uploads.map((upload) => html`<${UploadCard} key=${upload.localId} upload=${upload} />`)}</div>`}
      </section>

      <section className="panel">
        <div className="panel__header">
          <h2>Stored files</h2>
          <button type="button" className="ghost-button" onClick=${refreshFiles}>
            Refresh
          </button>
        </div>
        ${loadError ? html`<div className="empty-state empty-state--error">${loadError}</div>` : html`<${StoredFileTable} files=${storedFiles} />`}
      </section>
    </main>
  `;
}

createRoot(document.getElementById("root")).render(html`<${App} />`);
