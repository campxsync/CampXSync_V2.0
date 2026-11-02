import docx
import os

def dump_docx(file_path, out_path):
    doc = docx.Document(file_path)
    with open(out_path, "w", encoding="utf-8") as f:
        for p in doc.paragraphs:
            if p.text.strip():
                f.write(p.text + "\n")
        f.write("\n--- TABLES ---\n")
        for t_idx, table in enumerate(doc.tables):
            f.write(f"\n--- TABLE {t_idx+1} ---\n")
            for row in table.rows:
                row_text = [cell.text.strip().replace("\n", " ") for cell in row.cells]
                f.write(" | ".join(row_text) + "\n")

if __name__ == "__main__":
    stm02_docx = r"D:\CampXSync\Documentation\StudentModule\MicroServices\STM-02.docx"
    rbac_docx = r"D:\CampXSync\CampXSync_V2.0\docs\architecture\Security\CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx"
    
    os.makedirs("docs/audit", exist_ok=True)
    if os.path.exists(stm02_docx):
        dump_docx(stm02_docx, "docs/audit/STM-02_SPEC_DUMP.txt")
        print("Exported STM-02.docx to docs/audit/STM-02_SPEC_DUMP.txt")
    else:
        print("STM-02.docx not found at", stm02_docx)
        
    if os.path.exists(rbac_docx):
        dump_docx(rbac_docx, "docs/audit/RBAC_MATRIX_STUDENT_DUMP.txt")
        print("Exported RBAC docx to docs/audit/RBAC_MATRIX_STUDENT_DUMP.txt")
    else:
        print("RBAC docx not found at", rbac_docx)

