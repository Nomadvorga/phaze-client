# Signed module manifest

The client ships a pre-signed module catalog. The API verifies the Ed25519
signature before updating `known_modules`, so an arbitrary client cannot add
fake module identifiers to the admin panel.

The private key must stay outside this repository. By default the signing
script reads:

`%USERPROFILE%\.phaze-signing\manifest-ed25519-private.pem`

After adding, removing, renaming, or recategorizing a module, increment the
client version and sign the catalog:

```powershell
node scripts/module-manifest.mjs sign 1.1
```

To use a different key location:

```powershell
$env:PHAZE_MANIFEST_PRIVATE_KEY = "D:\secure\manifest-ed25519-private.pem"
node scripts/module-manifest.mjs sign 1.1
```

The script increments `manifestVersion` automatically. To set an explicit
version during recovery or key rotation, pass it as the second argument:

```powershell
node scripts/module-manifest.mjs sign 1.1 42
```

Every Gradle build runs the verification command automatically and fails if
the signature is invalid or the signed catalog no longer matches `Main.java`.

The committed public key must match the key in
`phaze-rules-admin/functions/_lib/manifest.ts`. Production can temporarily
override that value through `MANIFEST_PUBLIC_KEY` during a coordinated key
rotation.
