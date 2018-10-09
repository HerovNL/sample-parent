package com.sample.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.file.*;
import java.util.EnumSet;
import java.util.Set;


public class FileAccess {
    public static final Set<StandardOpenOption> READ      = EnumSet.of(StandardOpenOption.READ);
    public static final Set<StandardOpenOption> OVERWRITE = EnumSet.of(StandardOpenOption.CREATE,
                                                                       StandardOpenOption.WRITE);

    public static void copyByPath(Path from, Path to) throws IOException {
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static void copyByChannel(Path from, Path to) throws IOException {
        try (FileChannel fromChannel = FileChannel.open(from, READ);
             FileChannel toChannel = FileChannel.open(to, OVERWRITE)
        ) {
            fromChannel.transferTo(0L, fromChannel.size(), toChannel);
        }
    }


    public static String readByChannel(Path from, String encode) throws IOException {
        try (FileChannel channel = FileChannel.open(from, READ);
        ) {
            Charset        charset = Charset.forName(encode);
            CharsetDecoder decoder = charset.newDecoder();
            ByteBuffer     buffer  = ByteBuffer.allocateDirect((int) channel.size());
            channel.read(buffer);
            buffer.flip();
            CharBuffer charBuf = decoder.decode(buffer);
            return charBuf.toString();
        }
    }

    public static void writeByChannel(Path to, String text, String encode) throws IOException {
        try (FileChannel channel = FileChannel.open(to, OVERWRITE);
        ) {
            ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(encode));
            channel.write(buffer);
        }
    }

    public static void main(String[] args) throws IOException {
        Path from = Paths.get("C:\\develop\\node\\server.js");
        Path to   = Paths.get("C:\\develop\\node\\server2.js");
        //copyByPath(from, to);
        String text = readByChannel(from, "UTF-8");
        System.out.println(text);
        writeByChannel(to, text, "UTF-8");
    }
}
