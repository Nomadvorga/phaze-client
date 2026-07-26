package vorga.phazeclient.base.util.render.shader;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Shader {
    private final int programId;
    private final String vertexPath;
    private final String fragmentPath;

    public Shader(String folder, String name) {
        this.vertexPath = "/assets/Phaze/shaders/" + folder + "/" + name + ".vsh";
        this.fragmentPath = "/assets/Phaze/shaders/" + folder + "/" + name + ".fsh";

        int vertexId = createShader(vertexPath, GL20.GL_VERTEX_SHADER);
        int fragmentId = createShader(fragmentPath, GL20.GL_FRAGMENT_SHADER);

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexId);
        GL20.glAttachShader(programId, fragmentId);
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Failed to link shader program: " + GL20.glGetProgramInfoLog(programId));
        }

        GL20.glDeleteShader(vertexId);
        GL20.glDeleteShader(fragmentId);
    }

    private int createShader(String path, int type) {
        try {
            String source = readShaderFile(path);
            int shaderId = GL20.glCreateShader(type);
            GL20.glShaderSource(shaderId, source);
            GL20.glCompileShader(shaderId);

            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
                throw new RuntimeException("Failed to compile shader: " + path + "\n" + GL20.glGetShaderInfoLog(shaderId));
            }

            return shaderId;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + path, e);
        }
    }

    private String readShaderFile(String path) throws IOException {
        InputStream stream = Shader.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Shader file not found: " + path);
        }

        StringBuilder source = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append("\n");
            }
        }
        return source.toString();
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void setUniform1i(String name, int value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    public void setUniform1f(String name, float value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    public void setUniform2f(String name, float x, float y) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    public void setUniform2f(String name, Vector2f vec) {
        setUniform2f(name, vec.x, vec.y);
    }

    public void setUniform3f(String name, float x, float y, float z) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    public void setUniform3f(String name, Vector3f vec) {
        setUniform3f(name, vec.x, vec.y, vec.z);
    }

    public void setUniformBool(String name, boolean value) {
        setUniform1i(name, value ? 1 : 0);
    }

    public void delete() {
        GL20.glDeleteProgram(programId);
    }
}
