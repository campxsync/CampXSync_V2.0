const fs = require('fs');

function splitCsvLines(content) {
  const lines = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < content.length; i++) {
    const c = content[i];
    if (c === '"') {
      inQuotes = !inQuotes;
      cur += c;
    } else if ((c === '\n' || c === '\r') && !inQuotes) {
      if (c === '\r' && content[i+1] === '\n') i++;
      if (cur.trim().length > 0) lines.push(cur);
      cur = '';
    } else {
      cur += c;
    }
  }
  if (cur.trim().length > 0) lines.push(cur);
  return lines;
}

function parseCsvFields(line) {
  const fields = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
      if (inQuotes && line[i+1] === '"') {
        cur += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (c === ',' && !inQuotes) {
      fields.push(cur);
      cur = '';
    } else {
      cur += c;
    }
  }
  fields.push(cur);
  return fields;
}

const files = [
  { mod: 'ACD-01', file: 'ACD-01_Course_Management_User_Stories.csv', prefix: 'ACD01-STORY-' },
  { mod: 'ACD-02', file: 'ACD-02_Curriculum_Management_User_Stories.csv', prefix: 'ACD02-STORY-' },
  { mod: 'ACD-03', file: 'ACD-03_Subject_Management_User_Stories.csv', prefix: 'ACD03-STORY-' },
  { mod: 'ACD-04', file: 'ACD-04_Batch_Management_User_Stories.csv', prefix: 'ACD04-STORY-' },
  { mod: 'ACD-05', file: 'ACD-05_Timetable_Management_User_Stories.csv', prefix: 'ACD05-STORY-' },
  { mod: 'ACD-06', file: 'ACD-06_Attendance_Management_User_Stories.csv', prefix: 'ACD06-STORY-' },
  { mod: 'ACD-07', file: 'ACD-07_academic_calendar_user_stories.csv', prefix: 'ACD07-STORY-' },
  { mod: 'ACD-08', file: 'ACD-08_learning_resources_user_stories_REFINED.csv', prefix: 'ACD08-STORY-' },
  { mod: 'ACD-09', file: 'ACD-09_assessment_mapping_user_stories.csv', prefix: 'ACD09-STORY-' },
  { mod: 'ACD-10', file: 'ACD-10_Reporting_Analytics_User_Stories.csv', prefix: 'ACD10-STORY-' }
];

let allParsed = [];

files.forEach(m => {
  const content = fs.readFileSync('docs/user-stories/' + m.file, 'utf8').replace(/^\uFEFF/, '');
  const lines = splitCsvLines(content);
  const header = parseCsvFields(lines[0]);
  
  let storyIdx = 1;
  let modStories = [];

  for (let i = 1; i < lines.length; i++) {
    const fields = parseCsvFields(lines[i]);
    const firstCol = fields[0] ? fields[0].trim() : '';

    let isStory = false;
    let storyId = '';
    let summary = '';
    let epic = '';
    let criteria = '';

    if (m.mod === 'ACD-01' || m.mod === 'ACD-02' || m.mod === 'ACD-03' || m.mod === 'ACD-04' || m.mod === 'ACD-05' || m.mod === 'ACD-06') {
      if (firstCol.toLowerCase() === 'story') {
        isStory = true;
        storyId = m.prefix + String(storyIdx).padStart(2, '0');
        summary = fields[2] || '';
        epic = fields[4] || '';
        criteria = fields[9] || fields[3] || '';
        storyIdx++;
      }
    } else {
      // ACD-07, 08, 09, 10 have Story ID in first col like ACD07-US-001, ACD08-US-001, etc.
      if (firstCol.match(/^ACD[0-9]+-US-[0-9]+/i)) {
        isStory = true;
        storyId = firstCol;
        epic = fields[1] || '';
        summary = fields[3] || fields[2] || '';
        criteria = fields[4] || '';
        storyIdx++;
      }
    }

    if (isStory) {
      modStories.push({
        id: storyId,
        module: m.mod,
        epic: epic,
        summary: summary,
        criteria: criteria
      });
    }
  }

  console.log(`${m.mod} (${m.file}): found ${modStories.length} stories`);
  allParsed = allParsed.concat(modStories);
});

console.log('Total ACD parsed stories across ACD-01 to ACD-10:', allParsed.length);
fs.writeFileSync('docs/audit/ACD_ALL_STORIES.json', JSON.stringify(allParsed, null, 2), 'utf8');
