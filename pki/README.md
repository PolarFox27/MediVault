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

### Initial Setup (run once)
```bash
cd pki/scripts
./init-pki.sh
```

### Issue a Doctor Certificate
```bash
cd pki/scripts
./issue-doctor-cert.sh "Dr. John Smith" "Brussels Hospital"
```

### Renew a Doctor Certificate (daily)
```bash
./issue-doctor-cert.sh "Dr. John Smith" "Brussels Hospital"
```

### Import Certificate to Browser
1. Import `doctors/<name>/doctor.p12` into browser
2. Password is displayed when certificate is created

## Server Configuration

The server trusts only the Intermediate CA certificate. It:
1. Requires client certificate for `/doctor/**` endpoints
2. Validates certificate chain (Doctor → Intermediate → Root)
3. Extracts doctor identity from certificate Subject (CN, O)

## Security Notes

⚠️ **PROTECT THE ROOT CA KEY** - Store offline, ideally on air-gapped machine
⚠️ **Intermediate key** should be protected but can be on server for automation
⚠️ **Doctor private keys** should never leave the doctor's device in production
