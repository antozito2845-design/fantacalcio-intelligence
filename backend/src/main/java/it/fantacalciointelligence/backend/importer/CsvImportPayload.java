package it.fantacalciointelligence.backend.importer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import org.springframework.web.multipart.MultipartFile;

import it.fantacalciointelligence.backend.common.BadRequestException;

public record CsvImportPayload(String filename, byte[] bytes) {
    public CsvImportPayload {
        bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    public static CsvImportPayload from(MultipartFile file, String fallback) {
        if (file == null) throw new BadRequestException("File CSV obbligatorio");
        try {
            String name=file.getOriginalFilename();
            return new CsvImportPayload(name==null||name.isBlank()?fallback:name,file.getBytes());
        } catch (java.io.IOException ex) {
            throw new BadRequestException("Impossibile leggere il file CSV",ex);
        }
    }

    public byte[] bytes(){return bytes==null?null:Arrays.copyOf(bytes,bytes.length);}
    public InputStream openStream(){return new ByteArrayInputStream(bytes);}
    public boolean empty(){return bytes==null||bytes.length==0;}
}
