-- Text extracted from the file by the OCR worker, and where that extraction stands.
ALTER TABLE documents ADD COLUMN text_status VARCHAR(16) NOT NULL DEFAULT 'NOT_EXTRACTED';
ALTER TABLE documents ADD COLUMN extracted_text TEXT;
