import {
    createPrivateKey,
    createPublicKey,
    sign,
    verify,
} from "node:crypto";
import {
    existsSync,
    mkdirSync,
    readFileSync,
    readdirSync,
    writeFileSync,
} from "node:fs";
import { homedir } from "node:os";
import { basename, dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = join(
    projectDir,
    "src/main/java/vorga/phazeclient/implement/features/modules"
);
const mainSource = join(
    projectDir,
    "src/main/java/vorga/phazeclient/core/Main.java"
);
const manifestPath = join(
    projectDir,
    "src/main/resources/phaze/module-manifest.json"
);
const signaturePath = join(
    projectDir,
    "src/main/resources/phaze/module-manifest.sig"
);
const publicKeyPath = join(
    projectDir,
    "security/manifest-ed25519-public.pem"
);

function walk(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) =>
        entry.isDirectory()
            ? walk(join(directory, entry.name))
            : [join(directory, entry.name)]
    );
}

function extractRegisteredClassNames() {
    const source = readFileSync(mainSource, "utf8");
    const names = [
        ...source.matchAll(
            /moduleProvider\.getModules\(\)\.add\((?:[\w.]+\.)?([A-Za-z0-9_]+)\.getInstance\(\)\)/g
        ),
    ].map((match) => match[1]);
    return [...new Set(names)];
}

function normalizeIdentifier(raw) {
    const localized = raw.match(/^module\.(.+)\.name$/);
    return (localized ? localized[1] : raw).toLowerCase();
}

function buildManifest(clientVersion, manifestVersion) {
    const moduleFiles = walk(sourceRoot).filter((file) => file.endsWith(".java"));
    const byClassName = new Map(
        moduleFiles.map((file) => [basename(file, ".java"), file])
    );

    const modules = extractRegisteredClassNames().map((className) => {
        const file = byClassName.get(className);
        if (!file) {
            throw new Error(`Registered module ${className} has no source file`);
        }
        const source = readFileSync(file, "utf8");
        const extendsMatch = source.match(
            /class\s+[A-Za-z0-9_]+\s+extends\s+([A-Za-z0-9_]+)/
        );
        const superMatch = source.match(/super\s*\(([\s\S]{0,500}?)\);/);
        if (!superMatch) {
            throw new Error(`Cannot locate constructor super(...) for ${className}`);
        }
        const args = superMatch[1];
        const strings = [
            ...args.matchAll(/"([^"\\]*(?:\\.[^"\\]*)*)"/g),
        ].map((match) => match[1]);
        if (strings.length === 0) {
            throw new Error(`Cannot locate module id for ${className}`);
        }
        const explicitCategory = args.match(/ModuleCategory\.([A-Z_]+)/)?.[1];
        const parentClass = extendsMatch?.[1] ?? "";
        const category =
            explicitCategory ??
            (parentClass === "RectHudModule"
                ? "HUD"
                : basename(dirname(file)).toUpperCase());

        return {
            id: normalizeIdentifier(strings[0]),
            name: strings[1] ?? strings[0],
            category,
        };
    });

    modules.sort((a, b) => a.id.localeCompare(b.id));
    const duplicate = modules.find(
        (module, index) => index > 0 && module.id === modules[index - 1].id
    );
    if (duplicate) {
        throw new Error(`Duplicate module id: ${duplicate.id}`);
    }

    return {
        schemaVersion: 1,
        manifestVersion,
        clientVersion,
        modules,
    };
}

function encodeManifest(manifest) {
    return Buffer.from(`${JSON.stringify(manifest, null, 2)}\n`, "utf8");
}

function verifyStoredManifest() {
    if (!existsSync(manifestPath) || !existsSync(signaturePath)) {
        throw new Error(
            "Signed module manifest is missing. Run: node scripts/module-manifest.mjs sign"
        );
    }

    const payload = readFileSync(manifestPath);
    const signature = Buffer.from(
        readFileSync(signaturePath, "utf8").trim(),
        "base64"
    );
    const publicKey = createPublicKey(readFileSync(publicKeyPath, "utf8"));
    if (!verify(null, payload, publicKey, signature)) {
        throw new Error("Module manifest signature is invalid");
    }

    const stored = JSON.parse(payload.toString("utf8"));
    const expected = buildManifest(stored.clientVersion, stored.manifestVersion);
    if (JSON.stringify(stored) !== JSON.stringify(expected)) {
        throw new Error(
            "Module registry differs from the signed manifest. Re-sign it before building."
        );
    }
    process.stdout.write(
        `Verified signed manifest #${stored.manifestVersion} (schema v${stored.schemaVersion}): ${stored.modules.length} modules\n`
    );
}

function signManifest() {
    const privateKeyPath =
        process.env.PHAZE_MANIFEST_PRIVATE_KEY ??
        join(homedir(), ".phaze-signing/manifest-ed25519-private.pem");
    if (!existsSync(privateKeyPath)) {
        throw new Error(
            `Manifest private key not found at ${privateKeyPath}. ` +
                "Set PHAZE_MANIFEST_PRIVATE_KEY to its path."
        );
    }
    const clientVersion =
        process.env.PHAZE_CLIENT_VERSION ??
        process.argv[3] ??
        "1.1";
    const requestedManifestVersion = Number.parseInt(
        process.env.PHAZE_MANIFEST_VERSION ?? process.argv[4] ?? "",
        10
    );
    let previousManifestVersion = 0;
    if (existsSync(manifestPath)) {
        try {
            previousManifestVersion =
                JSON.parse(readFileSync(manifestPath, "utf8")).manifestVersion ?? 0;
        } catch {
            previousManifestVersion = 0;
        }
    }
    const manifestVersion = Number.isSafeInteger(requestedManifestVersion)
        ? requestedManifestVersion
        : previousManifestVersion + 1;
    if (manifestVersion < 1) {
        throw new Error("Manifest version must be a positive integer");
    }
    const payload = encodeManifest(buildManifest(clientVersion, manifestVersion));
    const privateKey = createPrivateKey(readFileSync(privateKeyPath, "utf8"));
    const signature = sign(null, payload, privateKey);
    mkdirSync(dirname(manifestPath), { recursive: true });
    writeFileSync(manifestPath, payload);
    writeFileSync(signaturePath, `${signature.toString("base64")}\n`);
    verifyStoredManifest();
}

const command = process.argv[2] ?? "verify";
if (command === "sign") {
    signManifest();
} else if (command === "verify") {
    verifyStoredManifest();
} else {
    throw new Error(`Unknown command "${command}" (expected sign or verify)`);
}
