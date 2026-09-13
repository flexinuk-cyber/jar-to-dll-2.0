import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HeaderConverter {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: HeaderConverter <input.jar> <output.h>");
            System.exit(1);
        }

        String inputPath;
        String outputPath;

        if (args.length >= 3) {
            inputPath = args[1];
            outputPath = args[2];
        } else {
            inputPath = args[0];
            outputPath = args[1];
        }

        byte[] jarBytes = Files.readAllBytes(Paths.get(inputPath));
        System.out.println("Converting " + inputPath + " (" + jarBytes.length + " bytes) to " + outputPath);

        StringBuilder sb = new StringBuilder();
        sb.append("#ifndef CLASSES_JAR_H_\n");
        sb.append("#define CLASSES_JAR_H_\n\n");
        sb.append("#include <stddef.h>\n\n");
        sb.append("#ifdef __cplusplus\nextern \"C\" {\n#endif\n\n");

        sb.append("extern const unsigned char jar_data[").append(jarBytes.length).append("];\n");
        sb.append("const unsigned char jar_data[").append(jarBytes.length).append("] = {\n");

        for (int i = 0; i < jarBytes.length; i++) {
            sb.append(String.format("0x%02X", jarBytes[i] & 0xFF));
            if (i < jarBytes.length - 1) {
                sb.append(", ");
            }
            if ((i + 1) % 16 == 0) {
                sb.append("\n");
            }
        }

        sb.append("\n};\n\n");
        sb.append("extern const size_t jar_size;\n");
        sb.append("const size_t jar_size = ").append(jarBytes.length).append(";\n\n");
        sb.append("#ifdef __cplusplus\n}\n#endif\n\n");
        sb.append("#endif // CLASSES_JAR_H_\n");

        Files.write(Paths.get(outputPath), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Conversion complete! Generated " + outputPath);
    }
}
