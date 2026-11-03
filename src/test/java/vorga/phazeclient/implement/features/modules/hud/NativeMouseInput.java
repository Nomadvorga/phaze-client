package vorga.phazeclient.implement.features.modules.hud;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

/** Fabric's callbacks do not change GLFW's native button state; use this window's Win32 queue. */
final class NativeMouseInput implements AutoCloseable {
    private final WindowsMouse mouse;
    private Pointer targetWindow;
    private ClientGameTestContext activeContext;

    NativeMouseInput() {
        if (!Platform.isWindows()) throw new AssertionError("Native HUD mouse verification needs a Windows desktop");
        mouse = Native.load("user32", WindowsMouse.class);
    }

    void hold(ClientGameTestContext context) {
        activeContext = context;
        context.getInput().holdMouse(0);
        context.runOnClient(client -> {
            long window = client.getWindow().getHandle();
            targetWindow = new Pointer(GLFWNativeWin32.glfwGetWin32Window(window));
            postButton(GLFW.GLFW_PRESS);
        });
        awaitButton(context, GLFW.GLFW_PRESS);
    }

    void release(ClientGameTestContext context) {
        context.runOnClient(client -> postButton(GLFW.GLFW_RELEASE));
        context.getInput().releaseMouse(0);
        awaitButton(context, GLFW.GLFW_RELEASE);
    }

    private void postButton(int action) {
        // Dispatch through the real GLFW Win32 backend, never its private state
        // or a global desktop click that could reach an installed client.
        int message = action == GLFW.GLFW_PRESS ? 0x0201 : 0x0202;
        Pointer buttons = action == GLFW.GLFW_PRESS ? new Pointer(1) : Pointer.NULL;
        if (!mouse.PostMessageW(targetWindow, message, buttons, new Pointer(24 | (24 << 16)))) {
            throw new AssertionError("Could not post a mouse event to the test HWND: " + Native.getLastError());
        }
    }

    private void awaitButton(ClientGameTestContext context, int expected) {
        try {
            context.waitFor(client -> {
                GLFW.glfwPollEvents();
                return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == expected;
            });
        } catch (AssertionError failure) {
            throw inputFailure(context, expected == GLFW.GLFW_PRESS ? "press" : "release", failure);
        }
    }

    private AssertionError inputFailure(ClientGameTestContext context, String action, AssertionError cause) {
        String state = context.computeOnClient(client -> {
            long window = client.getWindow().getHandle();
            return "target=" + targetWindow + ", foreground=" + mouse.GetForegroundWindow()
                    + ", glfwFocus=" + GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED)
                    + ", glfwButton=" + GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    + ", hudPointer=" + client.mouse.getX() + "," + client.mouse.getY();
        });
        return new AssertionError("Native test-window mouse " + action + " was not delivered: " + state, cause);
    }

    @Override
    public void close() {
        if (activeContext != null) release(activeContext);
    }

    public interface WindowsMouse extends StdCallLibrary {
        Pointer GetForegroundWindow();
        boolean PostMessageW(Pointer window, int message, Pointer wParam, Pointer lParam);
    }
}
