(function () {
  "use strict";

  // 复现 Typora 的两种任务框，但不改导入的 Markdown：
  // 1. “完成”首列中的纯 `[ ]` / `[x]` 表格任务；
  // 2. 单独使用的 Markdown 任务列表 `- [ ]` / `- [x]`。
  // 点击后的完整勾选集合经 Android 桥保存到 App，自身源码快照始终只读。

  var TABLE_CELL_CLASS = "xlb-task-cell";
  var LIST_CHECKBOX_CLASS = "xlb-task-list-checkbox";
  var DONE_CLASS = "xlb-task-done";
  var HASH_ATTRIBUTE = "data-xlb-task-hash";
  var SOURCE_CHECKED_ATTRIBUTE = "data-xlb-task-source-checked";
  var STATE_VERSION = "v1";
  var FIELD_SEPARATOR = ";";
  var HASH_PATTERN = /^[0-9a-f]{12}$/;
  var BRIDGE_NAME = (document.currentScript && document.currentScript.getAttribute("data-study-task-bridge"))
    || "xlbStudyTaskCheckbox";

  var stateNode = document.getElementById("study-task-checkbox-initial-state");
  var initialState = {};
  try {
    initialState = JSON.parse((stateNode && stateNode.textContent) || "{}") || {};
  } catch (error) {
    initialState = {};
  }
  var SOURCE_HASH = typeof initialState.sourceHash === "string" ? initialState.sourceHash.toLowerCase() : "";
  if (!HASH_PATTERN.test(SOURCE_HASH)) SOURCE_HASH = "";
  var STORED_STATE = typeof initialState.storedState === "string" ? initialState.storedState : "";

  var tableIndexes = new Map();
  Array.prototype.slice.call(document.querySelectorAll("table")).forEach(function (table, index) {
    tableIndexes.set(table, index);
  });

  var tableCells = Array.prototype.slice.call(document.querySelectorAll("." + TABLE_CELL_CLASS));
  var listInputs = Array.prototype.slice.call(document.querySelectorAll("input." + LIST_CHECKBOX_CLASS));
  var listIndexes = new Map();
  listInputs.forEach(function (input, index) {
    listIndexes.set(input, index);
  });
  var tasks = tableCells.concat(listInputs);

  function isTableTask(element) {
    return element.classList.contains(TABLE_CELL_CLASS);
  }

  function sourceChecked(element) {
    return element.getAttribute(SOURCE_CHECKED_ATTRIBUTE) === "true";
  }

  function positionOf(element) {
    if (!isTableTask(element)) {
      return [-1, listIndexes.get(element), -1].join(FIELD_SEPARATOR);
    }
    var table = element.closest("table");
    var row = element.closest("tr");
    var tableIndex = tableIndexes.has(table) ? tableIndexes.get(table) : -1;
    var rowIndex = table && row ? Array.prototype.indexOf.call(table.rows, row) : -1;
    return [tableIndex, rowIndex, element.cellIndex].join(FIELD_SEPARATOR);
  }

  function stateKey(element) {
    return positionOf(element) + FIELD_SEPARATOR + (element.getAttribute(HASH_ATTRIBUTE) || "");
  }

  function isChecked(element) {
    return isTableTask(element)
      ? element.getAttribute("aria-checked") === "true"
      : Boolean(element.checked);
  }

  function setChecked(element, checked) {
    var next = Boolean(checked);
    if (isTableTask(element)) {
      element.setAttribute("aria-checked", next ? "true" : "false");
      var row = element.closest("tr");
      if (row) row.classList.toggle(DONE_CLASS, next);
      return;
    }

    element.disabled = false;
    element.removeAttribute("disabled");
    element.checked = next;
    var item = element.closest("li");
    if (item) item.classList.toggle(DONE_CLASS, next);
  }

  /**
   * 返回 null 表示没有可用的 App 覆盖状态，此时使用 Markdown 自带的 `[ ]` / `[x]`；
   * 返回空 Set 则表示用户明确把所有任务都取消勾选了。
   */
  function parseStoredMarks(encoded) {
    var lines = String(encoded || "")
      .split("\n")
      .map(function (line) { return line.trim(); })
      .filter(function (line) { return line.length > 0; });
    if (lines.length < 2 || lines[0] !== STATE_VERSION || lines[1] !== SOURCE_HASH) return null;

    var marks = new Set();
    lines.slice(2).forEach(function (line) {
      var parts = line.split(FIELD_SEPARATOR);
      if (parts.length !== 4 || !HASH_PATTERN.test(parts[3])) return;
      var tableIndex = Number(parts[0]);
      var rowIndex = Number(parts[1]);
      var columnIndex = Number(parts[2]);
      if (!Number.isInteger(tableIndex) || !Number.isInteger(rowIndex) || !Number.isInteger(columnIndex)) return;
      if (rowIndex < 0 || columnIndex < -1) return;
      marks.add([tableIndex, rowIndex, columnIndex, parts[3]].join(FIELD_SEPARATOR));
    });
    return marks;
  }

  function encodeState() {
    if (!SOURCE_HASH) return null;
    var marks = tasks
      .filter(isChecked)
      .map(stateKey)
      .filter(function (key) { return HASH_PATTERN.test(key.split(FIELD_SEPARATOR)[3] || ""); })
      .sort();
    // 即使 marks 为空也保留版本与来源哈希，才能记住“把 Markdown 原有 `[x]` 取消掉”。
    return [STATE_VERSION, SOURCE_HASH].concat(marks).join("\n");
  }

  function report() {
    var encoded = encodeState();
    STORED_STATE = encoded || "";
    var bridge = window[BRIDGE_NAME];
    if (!bridge) return;
    try {
      bridge.onTaskStateChanged(encoded);
    } catch (error) {
      /* 页面交互已经完成，桥接失败不能让阅读器停摆。 */
    }
  }

  var storedMarks = parseStoredMarks(STORED_STATE);
  tasks.forEach(function (element) {
    var checked = storedMarks === null
      ? sourceChecked(element)
      : storedMarks.has(stateKey(element));
    setChecked(element, checked);
  });

  document.addEventListener("click", function (event) {
    if (!(event.target instanceof Element)) return;
    var task = event.target.closest("." + TABLE_CELL_CLASS);
    if (!task) return;

    event.preventDefault();
    event.stopPropagation();
    setChecked(task, !isChecked(task));
    // 不做延迟合并：用户点完立刻返回时，最后一次状态也必须已经交给 App。
    report();
  }, true);

  // 原生 input 会在 click 事件分发前切换 checked，并在 preventDefault 后回滚。
  // 因此列表任务监听 change，等浏览器完成切换后再保存最终值；表格里的伪复选框
  // 才由上面的 click 处理器手动翻转。
  document.addEventListener("change", function (event) {
    if (!(event.target instanceof Element)) return;
    var input = event.target.closest("input." + LIST_CHECKBOX_CLASS);
    if (!input) return;
    event.stopPropagation();
    setChecked(input, input.checked);
    report();
  }, true);

  document.addEventListener("keydown", function (event) {
    if (event.key !== " " && event.key !== "Enter") return;
    if (!(event.target instanceof Element)) return;
    var task = event.target.closest("." + TABLE_CELL_CLASS);
    if (!task) return;
    event.preventDefault();
    event.stopPropagation();
    setChecked(task, !isChecked(task));
    report();
  }, true);
})();
