package com.sample.common;

import sun.nio.ch.DirectBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;


public class IoUtil {
    // Options
    public static final Set<StandardOpenOption> READ_OPTIONS      = EnumSet.of(
            StandardOpenOption.READ
    );
    public static final Set<StandardOpenOption> OVERWRITE_OPTIONS = EnumSet.of(
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
    );
    public static final Set<StandardOpenOption> APPEND_OPTIONS    = EnumSet.of(
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
    );

    // ACL Permissions
    public static final  Set<AclEntryPermission> ACL_READ_MAX  = EnumSet.of(
            AclEntryPermission.READ_DATA,
            AclEntryPermission.READ_NAMED_ATTRS,
            AclEntryPermission.READ_ATTRIBUTES,
            AclEntryPermission.READ_ACL,
            AclEntryPermission.EXECUTE,        // Optional
            AclEntryPermission.SYNCHRONIZE);   // Can be opened for read/write by multiple processes, required
    public static final  Set<AclEntryPermission> ACL_WRITE_MAX = EnumSet.of(
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
    private static final Set<AclEntryPermission> ACL_READ_MIN  = EnumSet.of(
            AclEntryPermission.READ_DATA,
            AclEntryPermission.READ_NAMED_ATTRS,
            AclEntryPermission.READ_ATTRIBUTES,
            AclEntryPermission.SYNCHRONIZE
    );
    private static final Set<AclEntryPermission> ACL_WRITE_MIN = EnumSet.of(
            AclEntryPermission.READ_DATA,
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
            AclEntryPermission.WRITE_OWNER
    );


    /**
     * POSIX permissions creator, supported by all linux file systems; can provide common permissions
     */
    private static class PosixAccess {
        static final FileAttribute<Set<PosixFilePermission>> READ_ATTRS  = createPermission("r--------");
        static final FileAttribute<Set<PosixFilePermission>> WRITE_ATTRS = createPermission("rw-------");

        public static FileAttribute<Set<PosixFilePermission>> createPermission(String permissions) {
            return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions));
        }
    }

    /**
     * ACL permissions creator, supported by windows; can append ACL records(each record is one user's permissions).
     * Note: Some Linux file system such as XFS also support ACL, but the OS should have installed ACL software
     * by command like 'yum -y insatll libacl acl' or 'rpm -ivh libacl-x.x.xx-x.x acl-x.x.xx-x.x.rpm')
     */
    private static class AclAccess {
        static final String                        USER        = System.getProperty("user.name");
        static final FileAttribute<List<AclEntry>> READ_ATTRS  = createPermission(ACL_READ_MIN, USER);
        static final FileAttribute<List<AclEntry>> WRITE_ATTRS = createPermission(ACL_WRITE_MIN, USER);

        public static FileAttribute<List<AclEntry>> createPermission(Set<AclEntryPermission> permissions, String user) {
            FileAttribute<List<AclEntry>> result = new FileAttribute<List<AclEntry>>() {
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
            AclEntry.Builder builder = AclEntry.newBuilder();
            builder.setType(AclEntryType.ALLOW);
            try {
                UserPrincipal userPrincipal = FileSystems.getDefault()
                                                         .getUserPrincipalLookupService()
                                                         .lookupPrincipalByName(user);
                builder.setPrincipal(userPrincipal);
            } catch (IOException e) {
                throw new IllegalArgumentException("UserPrincipalNotFoundException");
            }
            builder.setPermissions(permissions);
            result.value().add(builder.build());
            return result;
        }
    }

    // OS File system type
    public static final boolean IS_POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    // Permissions for read/write
    private static final FileAttribute<?> READ_ATTRS  = IS_POSIX ? PosixAccess.READ_ATTRS : AclAccess.READ_ATTRS;
    private static final FileAttribute<?> WRITE_ATTRS = IS_POSIX ? PosixAccess.WRITE_ATTRS : AclAccess.WRITE_ATTRS;

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
     * Copy data by channel transfer: If target file exist, firstly truncate its data, otherwise create a new
     * file and copy data
     */
    public static void copy(Path from, Path to) throws IOException {
        try (FileChannel fromChannel = FileChannel.open(from, READ_OPTIONS);
             FileChannel toChannel = FileChannel.open(to, OVERWRITE_OPTIONS)
        ) {
            fromChannel.transferTo(0L, fromChannel.size(), toChannel);
        }
    }
    public static void secureCopy(Path from, Path to) throws IOException {
        try (FileChannel fromChannel = FileChannel.open(from, READ_OPTIONS, READ_ATTRS);
             FileChannel toChannel = FileChannel.open(to, OVERWRITE_OPTIONS, WRITE_ATTRS)
        ) {
            fromChannel.transferTo(0L, fromChannel.size(), toChannel);
        }
    }

    /**
     * Read text from file by direct buffer
     */
    public static String readText(Path from, String encode) throws IOException {
        return readTextFile(from, encode, false);
    }
    public static String secureReadText(Path from, String encode) throws IOException {
        return readTextFile(from, encode, true);
    }
    private static String readTextFile(Path from, String encode, boolean secure) throws IOException {
        try (FileChannel channel = secure ? FileChannel.open(from, READ_OPTIONS, READ_ATTRS)
                : FileChannel.open(from, READ_OPTIONS);
        ) {
            Charset        charset = Charset.forName(encode);
            CharsetDecoder decoder = charset.newDecoder();
            ByteBuffer     buffer  = ByteBuffer.allocateDirect((int) channel.size());
            channel.read(buffer);
            buffer.flip();
            CharBuffer charBuf = decoder.decode(buffer);
            String     result  = charBuf.toString();
            ((DirectBuffer) buffer).cleaner().clean();
            return result;
        }
    }

    /**
     * Write text to file
     */
    public static void writeText(Path to, String text, String encode) throws IOException {
        WriteTextFile(to, text, encode, false);
    }
    public static void secureWriteText(Path to, String text, String encode) throws IOException {
        WriteTextFile(to, text, encode, true);
    }
    public static void WriteTextFile(Path to, String text, String encode, boolean secure) throws IOException {
        try (FileChannel channel = secure ? FileChannel.open(to, OVERWRITE_OPTIONS, WRITE_ATTRS)
                : FileChannel.open(to, OVERWRITE_OPTIONS);
        ) {
            ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(encode));
            channel.write(buffer);
        }
    }

    /**
     * Get runtime folder by working class in jar or folder classes
     */
    public static Path getRuntimeFolder(Class workClass) {
        String location = workClass.getResource("").getPath();
        int    index    = location.lastIndexOf(".jar!/");
        if (index == -1) {
            index = location.lastIndexOf("classes/");
        }
        if (index == -1) {
            throw new IllegalArgumentException("Invalid class folder");
        }
        int end = index;
        for (; end > 0; end--) {
            if (location.charAt(end) == '/') {
                break;
            }
        }
        index = location.indexOf("file:");
        int start = index == -1 ? 0 : 5;
        if (!IS_POSIX && location.charAt(start) == '/') {
            start++;
        }
        return Paths.get(location.substring(start, end));
    }
}
