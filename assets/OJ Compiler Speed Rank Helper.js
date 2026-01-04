// ==UserScript==
// @name         OJ Compiler Speed Rank Helper
// @namespace    http://tampermonkey.net/
// @version      1.5.2
// @description  OJ Compiler Speed Rank Helper
// @author       tsxb
// @match        *://d.buaa.edu.cn/https/*/assignment/ojprank/OJPRank.jsp*
// @match        *://*/assignment/ojprank/OJPRank.jsp?*
// @grant        none
// ==/UserScript==

const style = document.createElement('style');
style.innerHTML = `
        @keyframes oj-fade-in {
            from { opacity: 0; transform: scale(0.95); }
            to { opacity: 1; transform: scale(1); }
        }
        .oj-modal-overlay {
            position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0, 0, 0, 0.4); backdrop-filter: blur(4px);
            z-index: 9999; display: flex; justify-content: center; align-items: center;
        }
        .oj-modal-content {
            background: white; padding: 24px; border-radius: 16px;
            width: 100%; max-width: 1200px; max-height: 85vh; overflow-y: auto;
            box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1), 0 10px 10px -5px rgba(0,0,0,0.04);
            animation: oj-fade-in 0.2s ease-out; position: relative;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        }
        .oj-history-table {
            width: 100%; border-collapse: separate; border-spacing: 0; margin-top: 15px;
        }
        .oj-history-table th {
            background: #f8fafc; color: #64748b; font-weight: 600;
            padding: 12px; border-bottom: 2px solid #e2e8f0; text-align: center; font-size: 13px;
        }
        .oj-history-table td {
            padding: 12px 8px; border-bottom: 1px solid #f1f5f9; text-align: center; color: #334155; font-size: 13px;
        }
        .oj-history-table tr:hover { background-color: #f1f5f9; transition: 0.2s; }
        .oj-rank-badge {
            background: #dbeafe; color: #1e40af; padding: 2px 8px;
            border-radius: 12px; font-weight: bold; font-size: 14px;
        }
        .oj-close-btn {
            position: absolute; top: 15px; right: 15px; background: #f1f5f9;
            border: none; border-radius: 50%; width: 32px; height: 32px;
            cursor: pointer; display: flex; align-items: center; justify-content: center;
            color: #64748b; font-size: 20px; transition: 0.2s;
        }
        .oj-close-btn:hover { background: #e2e8f0; color: #1e293b; }
        .oj-del-btn {
            color: #ef4444; cursor: pointer; border: none; background: none;
            padding: 4px 8px; border-radius: 4px; transition: 0.2s;
        }
        .oj-del-btn:hover { background: #fee2e2; }
    `;
document.head.appendChild(style);

(function () {
    'use strict';

    const testCaseIndices = [3, 4, 5, 6, 7, 8, 9, 10];
    let currentTableStats = [];

    window.addEventListener('load', function () {
        // 增加一点延迟确保表格加载完毕
        setTimeout(initAvgRankSort, 500);
    });

    function getRank(score, allScores) {
        if (score === Infinity || isNaN(score)) return allScores.length;
        return allScores.filter(s => s < score).length + 1;
    }

    function initAvgRankSort() {
        const table = document.getElementById("rankTable");
        if (!table) return;

        const tbody = table.querySelector("tbody");
        const thead = table.querySelector("thead");
        if (!tbody || !thead) return;

        const rows = Array.from(tbody.querySelectorAll("tr"));
        if (rows.length === 0) return;

        // 1. 预解析数据
        const rowDataMap = rows.map(row => {
            const cells = row.children;
            const scores = testCaseIndices.map(idx => {
                const val = parseFloat(cells[idx].innerText.trim());
                return isNaN(val) ? Infinity : val;
            });
            return {
                originalRow: row,
                // 优化识别逻辑：通常当前用户行会有特殊背景色或加粗
                // 这里保留原逻辑但增加一个备选方案（如有特定ID更好）
                isCurrentUser: !row.innerText.includes('*'),
                id: cells[1]?.innerText.trim(),
                name: cells[2]?.innerText.trim(),
                scores: scores
            };
        });

        // 2. 构建分布
        currentTableStats = testCaseIndices.map((_, i) => {
            return rowDataMap.map(d => d.scores[i]).sort((a, b) => a - b);
        });

        let foundCurrentUser = null;

        // 3. 渲染排名并识别当前用户
        rowDataMap.forEach((data) => {
            let rankSum = 0;
            const rawScoresForStorage = [];

            testCaseIndices.forEach((colIdx, i) => {
                const score = data.scores[i];
                const rank = getRank(score, currentTableStats[i]);
                rankSum += rank;

                const scoreText = score === Infinity ? "N/A" : score;
                rawScoresForStorage.push(scoreText);

                const cell = data.originalRow.children[colIdx];
                cell.innerHTML = `${scoreText}<span style="color:#888;font-size:0.85em;margin-left:2px;">/${rank}</span>`;
            });

            const avgRank = (rankSum / testCaseIndices.length).toFixed(2);
            let avgCell = data.originalRow.querySelector(".avg-rank-cell");
            if (!avgCell) {
                avgCell = document.createElement("td");
                avgCell.className = "avg-rank-cell";
                avgCell.style.fontWeight = "bold";
                avgCell.style.color = "#007bff";
                data.originalRow.insertBefore(avgCell, data.originalRow.children[11] || null);
            }
            avgCell.innerText = avgRank;
            avgCell.setAttribute("data-avg-rank", avgRank);

            // 只有当这一行确定是当前用户时才记录
            if (data.isCurrentUser) {
                foundCurrentUser = {
                    id: data.id,
                    name: data.name,
                    scores: rawScoresForStorage,
                    time: new Date().toLocaleString('zh-CN')
                };
            }
        });

        if (!document.getElementById("avg-rank-th")) {
            const headerRow = thead.querySelector("tr");
            const newTh = document.createElement("th");
            newTh.id = "avg-rank-th";
            newTh.className = "tablesorter-header";
            newTh.style.cursor = "pointer";
            newTh.innerHTML = '<div class="tablesorter-header-inner" style="color:#007bff;font-weight:bold;">Avg Rank</div>';
            headerRow.insertBefore(newTh, headerRow.children[11] || null);
            newTh.onclick = () => {
                const sortedRows = Array.from(tbody.querySelectorAll("tr")).sort((a, b) => {
                    return parseFloat(a.querySelector(".avg-rank-cell").getAttribute("data-avg-rank")) -
                        parseFloat(b.querySelector(".avg-rank-cell").getAttribute("data-avg-rank"));
                });
                sortedRows.forEach((r, i) => {
                    const idxCell = r.children[0].querySelector('div') || r.children[0];
                    idxCell.innerText = i + 1;
                    tbody.appendChild(r);
                });
            };
        }

        if (foundCurrentUser) {
            saveUserHistory(foundCurrentUser);
        }

        if (!document.getElementById("oj-control-panel")) {
            addControlPanel(table, foundCurrentUser);
        }
    }

    function saveUserHistory(userData) {
        if (!userData.id || !userData.name) return;

        const key = `OJ_History_${userData.id}_${userData.name}`;
        let history = JSON.parse(localStorage.getItem(key) || "[]");

        if (history.length > 0) {
            const lastEntry = history[history.length - 1];

            const lastScoresStr = JSON.stringify(lastEntry.scores.map(s => String(s)));
            const currentScoresStr = JSON.stringify(userData.scores.map(s => String(s)));

            if (lastScoresStr === currentScoresStr) {
                console.log("OJ Helper: 分数未变化，跳过历史记录");
                return;
            }
        }

        history.push(userData);
        if (history.length > 50) history.shift();

        localStorage.setItem(key, JSON.stringify(history));
        console.log("OJ Helper: 检测到分数变动，已存入历史记录");
    }

    function addControlPanel(table, currentUser) {
        const container = document.createElement("div");
        container.id = "oj-control-panel";
        container.style.cssText = "float:right;margin-bottom:10px;";

        const historyBtn = createBtn("查看历史数据", "#007bff", "#0069d9");
        historyBtn.style.marginRight = "10px";
        historyBtn.onclick = () => showHistoryModal(currentUser);

        const exportBtn = createBtn("导出 CSV", "#5cb85c", "#4cae4c");
        exportBtn.onclick = () => exportTableToCSV(table, "oj_rank_export.csv");

        container.appendChild(historyBtn);
        container.appendChild(exportBtn);
        table.parentNode.insertBefore(container, table);
    }

    function createBtn(text, bgColor, hoverColor) {
        const btn = document.createElement("button");
        btn.innerText = text;
        btn.style.cssText = `padding:6px 12px;background:${bgColor};color:white;border:none;border-radius:4px;cursor:pointer;font-weight:bold;transition:0.2s;margin-left:5px;`;
        btn.onmouseover = () => btn.style.backgroundColor = hoverColor;
        btn.onmouseout = () => btn.style.backgroundColor = bgColor;
        return btn;
    }

    function showHistoryModal(currentUser) {
        if (!currentUser) return alert("未找到有效数据");
        const key = `OJ_History_${currentUser.id}_${currentUser.name}`;
        const history = JSON.parse(localStorage.getItem(key) || "[]");

        const processedHistory = history.map((entry, originalIndex) => {
            let rankSum = 0;
            const scoresWithCurrentRank = entry.scores.map((s, i) => {
                const rawVal = parseFloat(String(s));
                const score = isNaN(rawVal) ? Infinity : rawVal;
                const rank = getRank(score, currentTableStats[i]);
                rankSum += rank;
                return { score: score === Infinity ? "N/A" : score, rank };
            });
            const avg = (rankSum / testCaseIndices.length).toFixed(2);
            return { ...entry, scoresWithCurrentRank, avg, originalIndex };
        }).reverse();

        const modalOverlay = document.createElement("div");
        modalOverlay.className = "oj-modal-overlay";

        const content = document.createElement("div");
        content.className = "oj-modal-content";

        content.innerHTML = `
            <button class="oj-close-btn" id="closeM">&times;</button>
            <h2 style="margin: 0; color: #1e293b; font-size: 1.25rem;">${currentUser.name} 的历史表现</h2>
            <div style="overflow-x: auto;">
                <table class="oj-history-table">
                    <thead>
                        <tr>
                            <th>记录时间</th>
                            ${[1, 2, 3, 4, 5, 6, 7, 8].map(i => `<th>TC${i}</th>`).join('')}
                            <th>Avg Rank</th>
                            <th>管理</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${processedHistory.length ? processedHistory.map(h => `
                        <tr>
                            <td style="color: #94a3b8; white-space: nowrap;">${h.time}</td>
                            ${h.scoresWithCurrentRank.map(s => `
                                <td>
                                    <div style="display: flex; align-items: baseline;">
        <div style="font-weight: 500;">${s.score}</div>
        <div style="color: #94a3b8; font-size: 11px;">/${s.rank}</div>
    </div>
                                </td>
                            `).join('')}
                            <td><span class="oj-rank-badge">${h.avg}</span></td>
                            <td><button class="oj-del-btn del-btn" data-idx="${h.originalIndex}">删除数据</button></td>
                        </tr>`).join('') : '<tr><td colspan="11" style="padding: 40px; color: #94a3b8;">暂无变动记录</td></tr>'}
                    </tbody>
                </table>
            </div>
        `;

        modalOverlay.appendChild(content);
        document.body.appendChild(modalOverlay);

        document.getElementById("closeM").onclick = () => document.body.removeChild(modalOverlay);
        modalOverlay.onclick = (e) => {
            if (e.target === modalOverlay) document.body.removeChild(modalOverlay);
        };

        content.querySelectorAll(".del-btn").forEach(btn => {
            btn.onclick = (e) => {
                e.stopPropagation();
                if (!confirm("确定要移除这条记录吗？")) return;
                let h = JSON.parse(localStorage.getItem(key));
                h.splice(parseInt(btn.dataset.idx), 1);
                localStorage.setItem(key, JSON.stringify(h));
                document.body.removeChild(modalOverlay);
                showHistoryModal(currentUser);
            };
        });
    }

    function exportTableToCSV(table, filename) {
        const rows = Array.from(table.querySelectorAll("tr"));
        const csvContent = rows.map(row => {
            return Array.from(row.querySelectorAll("th, td"))
                .filter(c => c.style.display !== 'none' && !c.querySelector('button'))
                .map(c => {
                    let text = c.innerText.replace(/\//g, ' / ').replace(/\n/g, ' ');
                    return `"${text.trim().replace(/"/g, '""')}"`;
                }).join(",");
        }).join("\n");
        const blob = new Blob(["\uFEFF" + csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = filename;
        link.click();
    }

})();