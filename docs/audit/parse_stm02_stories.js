const fs = require('fs');
const path = require('path');

const csvPath = path.resolve('UserStories/STM-02_Updated_Architect_User_Stories.csv');

function parseCSV(text) {
  const rows = [];
  let row = [];
  let inQuotes = false;
  let currentStr = '';
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    const nextChar = text[i+1];
    if (char === '"') {
      if (inQuotes && nextChar === '"') {
        currentStr += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === ',' && !inQuotes) {
      row.push(currentStr);
      currentStr = '';
    } else if ((char === '\r' || char === '\n') && !inQuotes) {
      if (char === '\r' && nextChar === '\n') { i++; }
      row.push(currentStr);
      currentStr = '';
      if (row.length > 1 || (row.length === 1 && row[0] !== '')) rows.push(row);
      row = [];
    } else {
      currentStr += char;
    }
  }
  if (currentStr || row.length > 0) {
    row.push(currentStr);
    if (row.length > 1 || (row.length === 1 && row[0] !== '')) rows.push(row);
  }
  return rows;
}

const content = fs.readFileSync(csvPath, 'utf8');
const rows = parseCSV(content);
const headers = rows[0];
console.log('Total rows (including header):', rows.length);
console.log('Headers:', headers);

const items = rows.slice(1).map(r => {
  const obj = {};
  headers.forEach((h, idx) => obj[h] = r[idx]);
  return obj;
});

const enablers = items.filter(i => i['Issue Type'] === 'Enabler');
const stories = items.filter(i => i['Issue Type'] === 'Story');
console.log('Enablers count:', enablers.length);
console.log('Stories count:', stories.length);
console.log('Total items:', items.length);

const releases = {};
items.forEach(i => {
  const rel = i['Release'] || 'UNKNOWN';
  releases[rel] = (releases[rel] || 0) + 1;
});
console.log('Releases count distribution:', releases);

const storyPointsByRelease = {};
items.forEach(i => {
  const rel = i['Release'] || 'UNKNOWN';
  const pts = parseInt(i['Story Points (indicative)'] || '0', 10) || 0;
  storyPointsByRelease[rel] = (storyPointsByRelease[rel] || 0) + pts;
});
console.log('Story points by release:', storyPointsByRelease);

const openDecisions = new Set();
items.forEach(i => {
  if (i['Open Decisions']) {
    i['Open Decisions'].split(',').map(d => d.trim()).filter(Boolean).forEach(d => openDecisions.add(d));
  }
});
console.log('Distinct Open Decisions referenced:', Array.from(openDecisions).sort());

const roles = new Set();
items.forEach(i => {
  if (i['RBAC Role Mapping']) {
    roles.add(i['RBAC Role Mapping'].trim());
  }
});
console.log('Distinct RBAC Role Mappings:', Array.from(roles).sort());

const actors = new Set();
items.forEach(i => {
  if (i['Actor']) {
    actors.add(i['Actor'].trim());
  }
});
console.log('Distinct Actors:', Array.from(actors).sort());

fs.writeFileSync('docs/audit/STM-02_ALL_STORIES.json', JSON.stringify(items, null, 2));
console.log('Saved to docs/audit/STM-02_ALL_STORIES.json');

