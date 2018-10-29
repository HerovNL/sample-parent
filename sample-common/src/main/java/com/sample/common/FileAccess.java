package com.sample.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;


public class FileAccess {
    private static      Log                     LOGGER    = LogFactory.getLog(FileAccess.class);
    public static final Set<StandardOpenOption> READ      = EnumSet.of(StandardOpenOption.READ);
    public static final Set<StandardOpenOption> OVERWRITE = EnumSet.of(StandardOpenOption.CREATE,
                                                                       StandardOpenOption.TRUNCATE_EXISTING,
                                                                       StandardOpenOption.WRITE);
    public static final boolean                 IS_POSIX  = FileSystems.getDefault().supportedFileAttributeViews()
                                                                       .contains("posix");

    private static final Set<AclEntryPermission> ACL_READ_PERMISSIONS  = EnumSet.of(
            AclEntryPermission.READ_DATA,
            AclEntryPermission.READ_NAMED_ATTRS,
            AclEntryPermission.READ_ATTRIBUTES,
            AclEntryPermission.READ_ACL,
            AclEntryPermission.EXECUTE,        // Optional
            AclEntryPermission.SYNCHRONIZE);   // Can be opened for read/write by multiple processes, should have
    private static final Set<AclEntryPermission> ACL_WRITE_PERMISSIONS = EnumSet.of(
            AclEntryPermission.READ_DATA,
            AclEntryPermission.READ_NAMED_ATTRS,
            AclEntryPermission.READ_ATTRIBUTES,
            AclEntryPermission.READ_ACL,
            AclEntryPermission.EXECUTE,
            AclEntryPermission.SYNCHRONIZE,    // Can be opened for read/write by multiple processes
            AclEntryPermission.WRITE_DATA,     // Modify data for file or add file for directory
            AclEntryPermission.APPEND_DATA,    // Append data for file or add subdirectory for directory
            AclEntryPermission.WRITE_NAMED_ATTRS,
            AclEntryPermission.WRITE_ATTRIBUTES,
            AclEntryPermission.WRITE_ACL,
            AclEntryPermission.WRITE_OWNER,
            AclEntryPermission.DELETE_CHILD,   // To delete a file/directory within a directory
            AclEntryPermission.DELETE
    );

    private interface IAccess {
        FileAttribute<?> createWritePermission();

        FileAttribute<?> createReadPermission();
    }

    /**
     * POSIX permissions creator, supported by all linux file systems; can provide common permissions
     */
    private static class POSIX_ACCESS {
        static final FileAttribute<Set<PosixFilePermission>> READ_PERMISSION  = createPermission("r--------");
        static final FileAttribute<Set<PosixFilePermission>> WRITE_PERMISSION = createPermission("rw-------");

        public static FileAttribute<Set<PosixFilePermission>> createPermission(String permissions) {
            return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions));
        }
    }

    /**
     * ACL permissions creator, supported by windows; can append ACL records(each record is one user's permissions).
     * Note: Some Linux file system such as XFS also support ACL, but the OS should have installed ACL software
     * by command like 'yum -y insatll libacl acl' or 'rpm -ivh libacl-x.x.xx-x.x acl-x.x.xx-x.x.rpm')
     */
    private static class ACL_ACCESS {
        public static FileAttribute<List<AclEntry>> createWritePermission() {
            FileAttribute<List<AclEntry>> result  = createEntries();
            AclEntry.Builder              builder = createBuilder(ACL_WRITE_PERMISSIONS);
            result.value().add(builder.build());
            return result;
        }

        public static FileAttribute<List<AclEntry>> createReadPermission() {
            FileAttribute<List<AclEntry>> result  = createEntries();
            AclEntry.Builder              builder = createBuilder(ACL_READ_PERMISSIONS);
            result.value().add(builder.build());
            return result;
        }

        private static FileAttribute<List<AclEntry>> createEntries() {
            return new FileAttribute<List<AclEntry>>() {
                final List<AclEntry> entries = new ArrayList<AclEntry>();

                @Override
                public String name() {
                    return "acl:acl";
                }

                @Override
                public List<AclEntry> value() {
                    return entries;
                }
            };
        }

        private static AclEntry.Builder createBuilder(Set<AclEntryPermission> permissions) {
            return createBuilder(permissions, System.getProperty("user.name"));
        }

        public static AclEntry.Builder createBuilder(Set<AclEntryPermission> permissions, String user) {
            AclEntry.Builder builder = AclEntry.newBuilder();
            builder.setType(AclEntryType.ALLOW);
            try {
                UserPrincipal userPrincipal = FileSystems.getDefault()
                                                         .getUserPrincipalLookupService().lookupPrincipalByName(user);
                builder.setPrincipal(userPrincipal);
            } catch (IOException e) {
                LOGGER.error("Failed to get user principal");
            }
            builder.setPermissions(permissions);
            return builder;
        }

        public static FileAttribute<List<AclEntry>> createPermission(Set<AclEntryPermission> permissions, String user) {
            FileAttribute<List<AclEntry>> result  = createEntries();
            AclEntry.Builder              builder = createBuilder(permissions, user);
            result.value().add(builder.build());
            return result;
        }
    }


    /**
     * Copy data and file attributes to target file: If target file exist, replace it with data and attributes
     *
     * @param from
     * @param to
     * @throws IOException
     */
    public static void copyWithAttributes(Path from, Path to) throws IOException {
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /**
     * Copy data to target file: If target file exist, firstly truncate its data, otherwise create a new
     * file and copy data
     *
     * @param from
     * @param to
     * @throws IOException
     */
    public static void copy(Path from, Path to) throws IOException {
        try (FileChannel fromChannel = FileChannel.open(from, READ);
             FileChannel toChannel = FileChannel.open(to, OVERWRITE)
        ) {
            fromChannel.transferTo(0L, fromChannel.size(), toChannel);
        }
    }


    public static String readText(Path from, String encode) throws IOException {
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


    public static void writeText(Path to, String text, String encode) throws IOException {
        try (FileChannel channel = FileChannel.open(to, OVERWRITE);
        ) {
            ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(encode));
            channel.write(buffer);
        }
    }

    public static void secureCopy(Path from, Path to) throws IOException {
        FileAttribute<?> readPermission;
        FileAttribute<?> writePermission;
        if (IS_POSIX) {
            readPermission = POSIX_ACCESS.READ_PERMISSION;
            writePermission = POSIX_ACCESS.WRITE_PERMISSION;
        } else {
            String user = System.getProperty("user.name");
            readPermission = ACL_ACCESS.createPermission(
                    EnumSet.of(AclEntryPermission.READ_DATA,
                               AclEntryPermission.READ_NAMED_ATTRS,
                               AclEntryPermission.READ_ATTRIBUTES,
                               AclEntryPermission.SYNCHRONIZE),
                    user);
            writePermission = ACL_ACCESS.createPermission(
                    EnumSet.of(AclEntryPermission.READ_DATA,
                               AclEntryPermission.READ_NAMED_ATTRS,
                               AclEntryPermission.READ_ATTRIBUTES,
                               AclEntryPermission.READ_ACL,
                               AclEntryPermission.SYNCHRONIZE,
                               AclEntryPermission.WRITE_DATA,
                               AclEntryPermission.APPEND_DATA,
                               AclEntryPermission.WRITE_NAMED_ATTRS,
                               AclEntryPermission.WRITE_ATTRIBUTES,
                               AclEntryPermission.DELETE,
                               AclEntryPermission.WRITE_ACL,
                               AclEntryPermission.WRITE_OWNER),
                    user);
        }

        try (SeekableByteChannel fromChannel = Files.newByteChannel(from, READ, readPermission);
             SeekableByteChannel toChannel = Files.newByteChannel(to, OVERWRITE, writePermission);) {
            ByteBuffer buffer = ByteBuffer.allocate((int) fromChannel.size());
            fromChannel.read(buffer);
            buffer.flip();
            toChannel.write(buffer);
        }
    }

    public static void main(String[] args) throws IOException {
        Path from = Paths.get("C:\\develop\\node\\server.js");
        Path to1  = Paths.get("C:\\develop\\node\\server1.js");
        Path to2  = Paths.get("C:\\develop\\node\\server2.js");
        Path to3  = Paths.get("C:\\develop\\node\\server2.js");
        copy(from, to1);
        secureCopy(from, to2);
        copyWithAttributes(from,to3);
    }
}
