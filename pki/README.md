# MediVault PKI Infrastructure

## Overview

This PKI implements a 3-tier certificate chain for doctor authentication:

```
Root CA (offline, 10 years)
    └── Intermediate CA (2 years)
            └── Doctor Certificates (24 hours)
```

## Security Design Decisions

### Why 3-tier hierarchy?
- **Root CA** is kept offline (air-gapped) - compromise would invalidate entire PKI
- **Intermediate CA** handles daily operations - can be revoked without rebuilding PKI
- **Separation of duties** - different keys for different purposes

### Why 24-hour doctor certificates?
- **Short-lived certificates** eliminate need for revocation infrastructure (CRL/OCSP)
- Reduces exposure window if private key is compromised
- Forces daily re-authentication (accountability)
- Modern zero-trust best practice (Google BeyondCorp approach)

### Why ECDSA P-384?
- 192-bit security level (equivalent to RSA-7680)
- Smaller keys, faster TLS handshakes
- NIST approved, widely supported

### Why CSR-based enrollment?
- Doctor's private key **never leaves their device**
- Provides **non-repudiation** - only doctor possesses private key
- Standard PKI practice

## Directory Structure

```
pki/
├── root-ca/           # ROOT CA (KEEP OFFLINE/SECURE)
│   ├── root-ca.key    # Root private key (PROTECT THIS!)
│   ├── root-ca.crt    # Root certificate
│   └── openssl.cnf    # Root CA config
├── intermediate-ca/   # Intermediate CA
│   ├── intermediate.key
│   ├── intermediate.crt
│   ├── intermediate-chain.crt  # Full chain for verification
│   └── openssl.cnf
├── doctors/           # Issued doctor certificates
│   └── <doctor-name>/
│       ├── doctor.key  # Doctor's private key
│       ├── doctor.csr  # Certificate signing request
│       ├── doctor.crt  # Signed certificate (24h validity)
│       └── doctor.p12  # PKCS#12 bundle for browser import
└── scripts/
    ├── init-pki.sh           # Initialize PKI (run once)
    └── issue-doctor-cert.sh  # Issue new doctor certificate
```

## Usage

### Step 1. Initial Setup (run once)

**Old PKI File Cleanup (if needed)**: only the `openssl.cnf` configs are preserved.
```bash
cd pki
rm -f root-ca/*.pem root-ca/*.crt root-ca/*.key root-ca/serial* root-ca/index.txt* root-ca/crlnumber*
rm -f intermediate-ca/*.pem intermediate-ca/*.crt intermediate-ca/*.csr intermediate-ca/*.key intermediate-ca/serial* intermediate-ca/index.txt* intermediate-ca/crlnumber* intermediate-ca/ca-chain.crt
rm -rf doctors/*
```
**Setup of a new PKI**
```bash
cd pki/scripts
./init-pki.sh
```

### Step 2. Issue a Doctor Certificate
```bash
cd pki/scripts
./issue-doctor-cert.sh "Dr. John Smith" "Brussels Hospital"
```

During certificate creation, the password for the `.p12` file is shown on screen. Store it somewhere safe.


### Step 3. Renew a Doctor Certificate (daily)
```bash
./issue-doctor-cert.sh "Dr. John Smith" "Brussels Hospital"
```

### Step 4. Import Certificate to Browser
1. Import `doctors/<name>/doctor.p12` into browser
2. Use the password displayed when the certificate is created


## Server Configuration

### Step 1. Configure the Server to trust the PKI

Once the PKI is set up as detailed above, configure the server to trust the intermediate CA:
```bash
cd server/src/main/resources/pki

rm -f truststore.p12 # remove previous truststore if needed
keytool -importcert -alias intermediate-ca \
  -file ../../../../../pki/intermediate-ca/intermediate.crt \
  -keystore truststore.p12 -storetype PKCS12 \
  -storepass <TRUSTSTORE_PASSWORD> -noprompt
```

Remember the value of `TRUSTSTORE_PASSWORD`, as the environment variable `SSL_TRUSTSTORE_PASSWORD` will need to store it for the server.

The server trusts only the Intermediate CA certificate. It:
1. Requires client certificate for `/doctor/**` endpoints
2. Validates certificate chain (Doctor → Intermediate → Root)
3. Extracts doctor identity from certificate Subject (CN, O)

### Step 2. Configure the Server SSL Certificate

This certificate allows the clients to connect to the server using TLS/HTTPS. 
In a production setting, a real certificate should be used, for example a free certificate provided by Let's Encrypt.
Since we are not in a production setting, we will use a self-signed certificate.

Do the following to create the self-signed certificate:
```bash
cd server/src/main/resources

rm -f medivault.p12 # remove previous keystore if needed
keytool -genkeypair \
  -alias selfsigned \
  -keyalg RSA \
  -keysize 4096 \
  -storetype PKCS12 \
  -keystore certificate.p12 \
  -validity 365 \
  -dname "CN=localhost, OU=Dev, O=Medivault, L=Brussels, S=Brussels, C=BE" \
  -storepass <KEYSTORE_PASSWORD>
```

Remember the value of `KEYSTORE_PASSWORD`, as the environment variable `SSL_KEYSTORE_PASSWORD` will need to store it for the server.

## Security Notes

⚠️ **PROTECT THE ROOT CA KEY** - Store offline, ideally on air-gapped machine

⚠️ **Intermediate key** should be protected but can be on server for automation

⚠️ **Doctor private keys** should never leave the doctor's device in production
