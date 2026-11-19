package com.swen3.dms.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOrderByUploadedAtDesc();

    /**
     * Case-insensitive search in title, file name and extracted text.
     *
     * @param pattern a LIKE pattern, lower-cased, with {@code \} as the escape character
     */
    @Query("""
            select d from Document d
            where lower(d.title) like :pattern escape '\\'
               or lower(d.originalFilename) like :pattern escape '\\'
               or lower(d.extractedText) like :pattern escape '\\'
            order by d.uploadedAt desc
            """)
    List<Document> search(@Param("pattern") String pattern);
}
