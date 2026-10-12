(() => {
  "use strict";

  const LANGUAGE_ALIASES = {
    "text/javascript": "javascript",
    js: "javascript",
    json: { name: "javascript", json: true },
    json5: { name: "javascript", json: true },
    ts: "text/typescript",
    typescript: "text/typescript",
    tsx: "text/typescript-jsx",
    jsx: "text/jsx",
    react: "text/jsx",
    html: "htmlmixed",
    html4: "htmlmixed",
    html5: "htmlmixed",
    xhtml: "htmlmixed",
    xml: "xml",
    svg: "xml",
    css: "css",
    less: "text/x-less",
    scss: "text/x-scss",
    sass: "text/x-sass",
    md: "gfm",
    mkd: "gfm",
    markdown: "gfm",
    python: "python",
    py: "python",
    cython: "text/x-cython",
    c: "text/x-csrc",
    clike: "text/x-csrc",
    cpp: "text/x-c++src",
    "c++": "text/x-c++src",
    cc: "text/x-c++src",
    csharp: "text/x-csharp",
    "c#": "text/x-csharp",
    cs: "text/x-csharp",
    java: "text/x-java",
    kotlin: "text/x-kotlin",
    scala: "text/x-scala",
    objc: "text/x-objectivec",
    "obj-c": "text/x-objectivec",
    "objective-c": "text/x-objectivec",
    swift: "text/x-swift",
    bash: "shell",
    sh: "shell",
    shell: "shell",
    zsh: "shell",
    powershell: "powershell",
    ps1: "powershell",
    sql: "text/x-sql",
    sqlite: "text/x-sql",
    mysql: "text/x-mysql",
    mariadb: "text/x-mariadb",
    mssql: "text/x-mssql",
    plsql: "text/x-plsql",
    yaml: "yaml",
    yml: "yaml",
    golang: "go",
    rb: "ruby",
    rs: "rust",
    kt: "text/x-kotlin",
    kts: "text/x-kotlin",
    docker: "dockerfile",
    make: "text/x-cmake",
    plaintext: "text/plain",
    text: "text/plain",
    txt: "text/plain",
  };

  const languageFromElement = (code) => {
    const className = Array.from(code.classList).find((name) => name.startsWith("language-"));
    const raw = className ? className.slice("language-".length) : code.getAttribute("lang") || "";
    return raw.toLowerCase().replace(/^\s*\.*lang(?:uage)*-/, "").replace(/[{}]/g, "").trim().split(/[\s,]+/)[0];
  };

  const highlightCodeBlocks = () => {
    const codeMirror = window.CodeMirror;
    if (!codeMirror || typeof codeMirror.runMode !== "function") return;

    document.querySelectorAll("#write pre > code").forEach((code) => {
      if (code.dataset.studyHighlighted === "1") return;
      const language = languageFromElement(code);
      if (!language || language === "text" || language === "txt" || language === "plaintext") return;
      const mode = Object.prototype.hasOwnProperty.call(LANGUAGE_ALIASES, language)
        ? LANGUAGE_ALIASES[language]
        : language;
      try {
        const resolved = codeMirror.getMode({ indentUnit: 2, tabSize: 4 }, mode);
        if (!resolved || resolved.name === "null") return;
        const source = code.textContent || "";
        code.textContent = "";
        code.classList.add("cm-s-inner");
        codeMirror.runMode(source, mode, code);
        code.dataset.studyHighlighted = "1";
      } catch (error) {
        // 未打包的冷门语言保持普通等宽文本，不影响整篇资料继续阅读。
      }
    });
  };

  const decodeUtf8Base64 = (encoded) => {
    try {
      let normalized = String(encoded || "").replace(/-/g, "+").replace(/_/g, "/");
      while (normalized.length % 4) normalized += "=";
      const binary = window.atob(normalized);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
      }
      return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    } catch (error) {
      return "";
    }
  };

  const installImageFallbacks = () => {
    document.querySelectorAll("#write img").forEach((image) => {
      if (image.dataset.studyImageBound === "1") return;
      image.dataset.studyImageBound = "1";

      const showMissingImage = () => {
        if (!image.isConnected || image.dataset.studyImageMissing === "1") return;
        image.dataset.studyImageMissing = "1";

        const source = image.getAttribute("src") || "未知路径";
        const alt = (image.getAttribute("alt") || "").trim();
        const placeholder = document.createElement("span");
        placeholder.className = "study-image-missing";
        placeholder.setAttribute("role", "img");
        placeholder.setAttribute("aria-label", alt ? `图片无法显示：${alt}` : "图片无法显示");

        const title = document.createElement("strong");
        title.textContent = alt || "图片无法显示";
        const path = document.createElement("code");
        path.textContent = source;
        placeholder.append(title, path);
        image.replaceWith(placeholder);
      };

      image.addEventListener("error", showMissingImage, { once: true });
      if (image.complete && image.naturalWidth === 0) showMissingImage();
    });
  };

  const installAnnotations = () => {
    const notes = Array.from(document.querySelectorAll("#write .typora-annotation[data-annotation]"));
    if (!notes.length) return;

    const card = document.createElement("div");
    card.id = "study-annotation-card";
    card.hidden = true;
    card.setAttribute("role", "dialog");
    card.setAttribute("aria-label", "注释内容");
    card.setAttribute("tabindex", "-1");
    document.body.appendChild(card);

    let active = null;

    const close = () => {
      if (active) active.setAttribute("aria-expanded", "false");
      active = null;
      card.hidden = true;
      card.textContent = "";
    };

    const position = () => {
      if (!active || card.hidden) return;
      const anchor = active.getBoundingClientRect();
      const margin = 12;
      const gap = 9;
      const width = card.offsetWidth;
      const height = card.offsetHeight;
      let left = anchor.left + Math.min(Math.max(anchor.width / 2 - width / 2, 0), 34);
      left = Math.max(margin, Math.min(left, window.innerWidth - width - margin));
      let top = anchor.bottom + gap;
      if (top + height > window.innerHeight - margin && anchor.top - height - gap >= margin) {
        top = anchor.top - height - gap;
      }
      top = Math.max(margin, Math.min(top, window.innerHeight - height - margin));
      card.style.left = `${Math.round(left)}px`;
      card.style.top = `${Math.round(top)}px`;
    };

    const open = (note) => {
      const text = decodeUtf8Base64(note.getAttribute("data-annotation"));
      if (!text) return;
      if (active && active !== note) active.setAttribute("aria-expanded", "false");
      active = note;
      note.setAttribute("aria-expanded", "true");
      card.textContent = text;
      card.hidden = false;
      card.style.visibility = "hidden";
      position();
      card.style.visibility = "visible";
    };

    notes.forEach((note) => {
      note.setAttribute("aria-expanded", "false");
      note.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        if (active === note && !card.hidden) close(); else open(note);
      });
      note.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          if (active === note && !card.hidden) close(); else open(note);
        }
      });
    });

    document.addEventListener("click", (event) => {
      if (!card.hidden && !card.contains(event.target) && event.target !== active) close();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !card.hidden) {
        const previous = active;
        close();
        if (previous) previous.focus({ preventScroll: true });
      }
    });
    window.addEventListener("resize", position);
    window.addEventListener("scroll", close, { passive: true });
  };

  const init = () => {
    highlightCodeBlocks();
    installImageFallbacks();
    installAnnotations();
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
