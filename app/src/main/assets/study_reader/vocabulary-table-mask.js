(function () {
  "use strict";

  // 「背单词」表格遮挡：点表头选一列遮起来，点单元格逐个翻开自测。
  // 遮挡与翻开的单元格必须跨进程保留——用户翻开的单词，下次进来还要是翻开的。
  //
  // 数据来源是下面的 JSON 块（Kotlin 侧 vocabularyTableMaskAssets 写入）：
  //   bridge       JS 桥接对象名，实际对象由 addJavascriptInterface 注入；
  //   enabled      进入这篇资料时遮挡开关是否已经打开；
  //   sourceHash   当前资料内容哈希，和保存时对不上就整体作废；
  //   storedState  上次的遮挡状态，格式 `表号:列号:正文哈希:已翻开行号,行号`。
  //
  // 这段脚本必须放在 assets 里而不是 Kotlin 的原始字符串常量里：Kotlin 的字符串
  // 模板会先改写美元符号开头的 JavaScript 内容，页面里跑的就不再是这里写的东西。

  var ACTIVE = "study-vocabulary-mask-active";
  var MASKED = "study-vocabulary-mask-cell";
  var REVEALED = "study-vocabulary-mask-revealed";
  var ENABLED = "study-vocabulary-mask-enabled";

  var initialStateNode = document.getElementById("study-vocabulary-mask-initial-state");
  var initialState = {};
  try {
    initialState = JSON.parse((initialStateNode && initialStateNode.textContent) || "{}") || {};
  } catch (error) {
    initialState = {};
  }
  var SOURCE_HASH = typeof initialState.sourceHash === "string" ? initialState.sourceHash : "";
  var STORED_STATE = typeof initialState.storedState === "string" && initialState.storedState
    ? initialState.storedState
    : null;
  var BRIDGE = initialState.bridge ? window[initialState.bridge] : null;

  // 脚本随资料注入在 <head> 附近，解析到它时表格往往还没建出来，因此真正的初始化
  // 放到 DOMContentLoaded；在此之前到达的 setEnabled 只记下来。
  var domReady = document.readyState !== "loading";
  var enabled = initialState.enabled === true;
  var pendingEnabled = null;
  var initialized = false;
  var selectedTable = null;
  var selectedColumn = -1;
  // 冻结当前这次遮挡的来源哈希：没有遮挡时为空，避免"什么都没遮"被记到别的资料上。
  var frozenHash = "";
  var restoring = false;

  function tables() {
    return Array.prototype.slice.call(document.querySelectorAll("table"));
  }

  // 遮挡状态只在用户操作后回报；还原过程本身不回报，避免把刚恢复的状态又写一遍。
  function report() {
    if (restoring) return;
    var encoded = selectedTable === null || selectedColumn < 0
      ? null
      : [
        tables().indexOf(selectedTable),
        selectedColumn,
        frozenHash,
        // 直接沿用 table.rows 的顺序逐行检查。真实 HTML 表格会自动插入
        // thead/tbody，不能假定 td -> tr -> table 只有两层。
        Array.prototype.slice.call(selectedTable.rows)
          .map(function (row, rowIndex) {
            var cell = Array.prototype.slice.call(row.cells).find(function (candidate) {
              return candidate.cellIndex === selectedColumn;
            });
            return rowIndex > 0 && cell && cell.classList.contains(REVEALED)
              ? rowIndex
              : -1;
          })
          .filter(function (rowIndex) { return rowIndex > 0; })
          .join(","),
      ].join(":");
    // 页面内也以最近一次操作为准。否则先翻开几个单元格、关闭再打开遮挡时，
    // 会错误恢复成刚进入页面时的旧状态。
    STORED_STATE = encoded;
    if (!BRIDGE) return;
    // 桥接方法抛错不能让阅读器停摆，遮挡本身在页面内已经生效。
    try {
      BRIDGE.onMaskStateChanged(encoded);
    } catch (error) {
      /* 忽略回报失败 */
    }
  }

  function clearSelection() {
    document.querySelectorAll("table." + ACTIVE).forEach(function (table) {
      table.classList.remove(ACTIVE);
    });
    document.querySelectorAll("." + MASKED).forEach(function (cell) {
      cell.classList.remove(MASKED, REVEALED);
    });
    selectedTable = null;
    selectedColumn = -1;
    frozenHash = "";
  }

  function applyColumn(table, columnIndex, revealedRows) {
    table.classList.add(ACTIVE);
    var rows = Array.prototype.slice.call(table.rows);
    rows.slice(1).forEach(function (row) {
      var cell = Array.prototype.slice.call(row.cells).find(function (candidate) {
        return candidate.cellIndex === columnIndex;
      });
      if (!cell) return;
      cell.classList.add(MASKED);
      // 行号就是表头之外的行下标，和 report() 用同一套取法，恢复与回报才对得上。
      if (revealedRows.has(rows.indexOf(row))) cell.classList.add(REVEALED);
    });
  }

  function selectColumn(table, columnIndex) {
    clearSelection();
    selectedTable = table;
    selectedColumn = columnIndex;
    frozenHash = SOURCE_HASH;
    applyColumn(table, columnIndex, new Set());
  }

  function restoreSelection(encoded) {
    if (!encoded) return;
    var parts = String(encoded).split(":");
    if (parts.length !== 4) return;
    var storedHash = parts[2];
    // 资料重新导入后哈希会变，旧坐标可能整体错位：过期就回到未遮挡。
    if (!storedHash || SOURCE_HASH.indexOf(storedHash) !== 0) return;
    var table = tables()[Number(parts[0])];
    var columnIndex = Number(parts[1]);
    var columnCount = table && table.rows.length ? table.rows[0].cells.length : 0;
    if (!table || !Number.isInteger(columnIndex) || columnIndex < 0 || columnIndex >= columnCount) return;
    var revealedRows = new Set(
      String(parts[3]).split(",").map(Number).filter(Number.isInteger),
    );
    restoring = true;
    try {
      clearSelection();
      selectedTable = table;
      selectedColumn = columnIndex;
      frozenHash = storedHash;
      applyColumn(table, columnIndex, revealedRows);
    } finally {
      restoring = false;
    }
  }

  function applyEnabled(nextEnabled, persistDisabled) {
    enabled = Boolean(nextEnabled);
    restoring = true;
    try {
      clearSelection();
      document.documentElement.classList.toggle(ENABLED, enabled);
      if (enabled) restoreSelection(STORED_STATE);
    } finally {
      restoring = false;
    }
    // 关闭遮挡就是“当前没有隐藏单元格”，必须把这个状态也交回 App；否则退出后
    // 旧遮挡会再次出现。初始化恢复本身不触发写入。
    if (!enabled && persistDisabled) {
      STORED_STATE = null;
      report();
    }
  }

  function init() {
    initialized = true;
    // 页面自己恢复：不依赖 Kotlin 侧 onPageFinished 的时序。
    var hadPendingEnabled = pendingEnabled !== null;
    applyEnabled(hadPendingEnabled ? pendingEnabled : enabled, hadPendingEnabled);
    pendingEnabled = null;
  }

  // Kotlin 侧的开关入口：页面还没初始化就只记状态，等 DOM 就绪后一起应用。
  window.studyVocabularyTableMask = {
    setEnabled: function (nextEnabled) {
      if (!initialized) {
        pendingEnabled = Boolean(nextEnabled);
        return;
      }
      applyEnabled(nextEnabled, true);
    },
  };

  document.addEventListener("click", function (event) {
    if (!enabled || !(event.target instanceof Element)) return;
    var cell = event.target.closest("th, td");
    var table = cell ? cell.closest("table") : null;
    if (!cell || !table) return;

    var rowIndex = Array.prototype.indexOf.call(table.rows, cell.parentElement);
    if (rowIndex === 0) {
      selectColumn(table, cell.cellIndex);
    } else if (table === selectedTable && cell.cellIndex === selectedColumn) {
      cell.classList.toggle(REVEALED);
    } else {
      return;
    }
    report();

    event.preventDefault();
    event.stopPropagation();
  }, true);

  if (domReady) {
    init();
  } else {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  }
})();
