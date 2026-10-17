import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

// Standalone JDK-only logic check. Does not invoke Gradle or compile the mod.
const root = fileURLToPath(new URL('../', import.meta.url));
const output = mkdtempSync(join(tmpdir(), 'phaze-pack-preview-'));
const tool = name => process.env.JAVA_HOME
    ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? `${name}.exe` : name)
    : name;
const run = (name, args) => {
    const result = spawnSync(tool(name), args, { cwd: root, stdio: 'inherit' });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${name} exited with ${result.status}`);
};
try {
    run('javac', ['--release', '21', '-d', output,
        resolve(root, 'src/main/java/vorga/phazeclient/implement/features/modules/other/PackPreviewAssetLogic.java'),
        resolve(root, 'scripts/PackPreviewAssetLogicTest.java')]);
    run('java', ['-cp', output, 'vorga.phazeclient.implement.features.modules.other.PackPreviewAssetLogicTest',
        resolve(root, 'src/main/java/vorga/phazeclient/implement/features/modules/other/PackPreviewAssets.java')]);
} finally {
    rmSync(output, { recursive: true, force: true });
}
