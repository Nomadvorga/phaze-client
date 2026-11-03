package vorga.phazeclient.test;

import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

/** Applies Fabric's normal mapped-package access repair, without starting a game/GL context. */
public final class HeadlessMinecraftChecks extends ClassLoader {
    private HeadlessMinecraftChecks(ClassLoader parent) { super(parent); }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected a check main class");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader loader = new HeadlessMinecraftChecks(previous);
        try {
            thread.setContextClassLoader(loader);
            loader.loadClass(args[0]).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw exception;
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                boolean local = name.startsWith("net.minecraft.") || name.startsWith("vorga.phazeclient.")
                        && !name.equals(HeadlessMinecraftChecks.class.getName());
                type = local ? findClass(name) : super.loadClass(name, false);
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream input = getParent().getResourceAsStream(resource)) {
            if (input == null) throw new ClassNotFoundException(name);
            byte[] bytes = input.readAllBytes();
            if (name.startsWith("net.minecraft.")) {
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new PackageAccessFixer(Opcodes.ASM9, writer), 0);
                bytes = writer.toByteArray();
            }
            int separator = name.lastIndexOf('.');
            if (separator > 0) {
                String packageName = name.substring(0, separator);
                if (getDefinedPackage(packageName) == null) definePackage(packageName, null, null, null, null, null, null, null);
            }
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException exception) {
            throw new ClassNotFoundException(name, exception);
        }
    }
}
