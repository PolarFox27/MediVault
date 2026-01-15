#!/bin/bash
# =============================================================================
# MediVault Doctor Certificate Issuance Script
# =============================================================================
# Issues a short-lived (24h) certificate for a doctor.
# 
# Usage:
#   ./issue-doctor-cert.sh "Dr. Full Name" "Organization Name"
#
# Example:
#   ./issue-doctor-cert.sh "Dr. Abanoub Ghobrial" "Brussels Hospital"
#
# Security notes:
# - Certificate validity: 24 hours (short-lived = no revocation needed)
# - Uses ECDSA P-384 (192-bit security)
# - Private key is generated locally (never transmitted)
# - PKCS#12 bundle created for browser import
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKI_DIR="$(dirname "$SCRIPT_DIR")"
INTERMEDIATE_CA_DIR="$PKI_DIR/intermediate-ca"
DOCTORS_DIR="$PKI_DIR/doctors"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# =============================================================================
# Argument validation
# =============================================================================
if [[ $# -lt 2 ]]; then
    echo -e "${RED}Usage: $0 \"Doctor Name\" \"Organization\"${NC}"
    echo -e "Example: $0 \"Dr. John Smith\" \"Brussels Hospital\""
    exit 1
fi

DOCTOR_NAME="$1"
ORGANIZATION="$2"

# Create safe directory name from doctor name
DOCTOR_DIR_NAME=$(echo "$DOCTOR_NAME" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | tr -cd '[:alnum:]-')
DOCTOR_DIR="$DOCTORS_DIR/$DOCTOR_DIR_NAME"

echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}  MediVault Doctor Certificate Issuance${NC}"
echo -e "${GREEN}=============================================${NC}"
echo -e "Doctor:       ${CYAN}$DOCTOR_NAME${NC}"
echo -e "Organization: ${CYAN}$ORGANIZATION${NC}"
echo -e "Directory:    ${CYAN}$DOCTOR_DIR${NC}"

# =============================================================================
# Check prerequisites
# =============================================================================
if [[ ! -f "$INTERMEDIATE_CA_DIR/intermediate.key" ]]; then
    echo -e "${RED}ERROR: Intermediate CA not found!${NC}"
    echo "Please run init-pki.sh first."
    exit 1
fi

# =============================================================================
# Create doctor directory
# =============================================================================
mkdir -p "$DOCTOR_DIR"
cd "$DOCTOR_DIR"

# =============================================================================
# Step 1: Generate doctor's private key
# =============================================================================
echo -e "\n${GREEN}[1/4] Generating private key (ECDSA P-384)...${NC}"

openssl ecparam -genkey -name secp384r1 -noout -out doctor.key
chmod 400 doctor.key

echo -e "${GREEN}✓ Private key generated${NC}"

# =============================================================================
# Step 2: Create Certificate Signing Request (CSR)
# =============================================================================
echo -e "\n${GREEN}[2/4] Creating Certificate Signing Request...${NC}"

# Create CSR with doctor's identity
openssl req -new \
    -key doctor.key \
    -sha384 \
    -subj "/C=BE/ST=Brussels/L=Brussels/O=$ORGANIZATION/CN=$DOCTOR_NAME" \
    -out doctor.csr

echo -e "${GREEN}✓ CSR created${NC}"

# =============================================================================
# Step 3: Sign CSR with Intermediate CA (24-hour validity)
# =============================================================================
echo -e "\n${GREEN}[3/4] Signing certificate (24-hour validity)...${NC}"

cd "$INTERMEDIATE_CA_DIR"

# Sign the certificate with 24-hour validity
openssl ca -config openssl.cnf \
    -extensions doctor_cert \
    -days 1 \
    -notext \
    -md sha384 \
    -batch \
    -in "$DOCTOR_DIR/doctor.csr" \
    -out "$DOCTOR_DIR/doctor.crt"

echo -e "${GREEN}✓ Certificate signed${NC}"

# =============================================================================
# Step 4: Create PKCS#12 bundle for browser import
# =============================================================================
echo -e "\n${GREEN}[4/4] Creating PKCS#12 bundle...${NC}"

cd "$DOCTOR_DIR"

# Generate a random password for PKCS#12
P12_PASSWORD=$(openssl rand -base64 16 | tr -d '/+=' | head -c 16)

# Create PKCS#12 bundle (includes private key, cert, and CA chain)
openssl pkcs12 -export \
    -out doctor.p12 \
    -inkey doctor.key \
    -in doctor.crt \
    -certfile "$INTERMEDIATE_CA_DIR/ca-chain.crt" \
    -name "$DOCTOR_NAME" \
    -passout "pass:$P12_PASSWORD"

chmod 400 doctor.p12

echo -e "${GREEN}✓ PKCS#12 bundle created${NC}"

# =============================================================================
# Summary
# =============================================================================
echo -e "\n${GREEN}=============================================${NC}"
echo -e "${GREEN}  Certificate Issued Successfully!${NC}"
echo -e "${GREEN}=============================================${NC}"

echo -e "\n${YELLOW}Certificate Details:${NC}"
openssl x509 -in doctor.crt -noout -subject -issuer -dates

echo -e "\n${YELLOW}Files created:${NC}"
echo "  Private key: $DOCTOR_DIR/doctor.key"
echo "  Certificate: $DOCTOR_DIR/doctor.crt"
echo "  PKCS#12:     $DOCTOR_DIR/doctor.p12"

echo -e "\n${CYAN}=============================================${NC}"
echo -e "${CYAN}  PKCS#12 Password: $P12_PASSWORD${NC}"
echo -e "${CYAN}=============================================${NC}"

echo -e "\n${YELLOW}To import into browser:${NC}"
echo "1. Open browser settings → Certificates"
echo "2. Import '$DOCTOR_DIR/doctor.p12'"
echo "3. Enter password: $P12_PASSWORD"

echo -e "\n${RED}⚠️  Certificate expires in 24 hours!${NC}"
echo -e "${RED}Run this script again to renew.${NC}"

# Verify the certificate chain
echo -e "\n${YELLOW}Verifying certificate chain...${NC}"
openssl verify -CAfile "$INTERMEDIATE_CA_DIR/ca-chain.crt" doctor.crt
