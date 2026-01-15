#!/bin/bash
# =============================================================================
# MediVault PKI Initialization Script
# =============================================================================
# This script creates the Root CA and Intermediate CA.
# Run this ONCE to set up the PKI infrastructure.
#
# Security notes:
# - Root CA key should be moved to offline storage after creation
# - Uses ECDSA P-384 (192-bit security level)
# - Root CA: 10 years validity
# - Intermediate CA: 2 years validity
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKI_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_CA_DIR="$PKI_DIR/root-ca"
INTERMEDIATE_CA_DIR="$PKI_DIR/intermediate-ca"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}  MediVault PKI Initialization${NC}"
echo -e "${GREEN}=============================================${NC}"

# Check if PKI already exists
if [[ -f "$ROOT_CA_DIR/root-ca.key" ]]; then
    echo -e "${YELLOW}WARNING: Root CA already exists!${NC}"
    echo "If you continue, the existing PKI will be DESTROYED."
    read -p "Are you sure you want to continue? (yes/no): " confirm
    if [[ "$confirm" != "yes" ]]; then
        echo "Aborted."
        exit 1
    fi
fi

# =============================================================================
# Step 1: Create Root CA
# =============================================================================
echo -e "\n${GREEN}[1/4] Creating Root CA...${NC}"

cd "$ROOT_CA_DIR"

# Initialize CA database files
echo "01" > serial
touch index.txt
echo "01" > crlnumber

# Generate Root CA private key (ECDSA P-384)
echo "Generating Root CA private key (ECDSA P-384)..."
openssl ecparam -genkey -name secp384r1 -noout -out root-ca.key
chmod 400 root-ca.key

# Generate Root CA certificate (10 years = 3650 days)
echo "Generating Root CA certificate (10 years validity)..."
openssl req -config openssl.cnf \
    -key root-ca.key \
    -new -x509 \
    -days 3650 \
    -sha384 \
    -extensions v3_ca \
    -out root-ca.crt

echo -e "${GREEN}✓ Root CA created${NC}"
openssl x509 -in root-ca.crt -noout -subject -dates

# =============================================================================
# Step 2: Create Intermediate CA
# =============================================================================
echo -e "\n${GREEN}[2/4] Creating Intermediate CA...${NC}"

cd "$INTERMEDIATE_CA_DIR"

# Initialize CA database files
echo "01" > serial
touch index.txt
echo "01" > crlnumber

# Generate Intermediate CA private key (ECDSA P-384)
echo "Generating Intermediate CA private key (ECDSA P-384)..."
openssl ecparam -genkey -name secp384r1 -noout -out intermediate.key
chmod 400 intermediate.key

# Generate Intermediate CA CSR
echo "Generating Intermediate CA certificate signing request..."
openssl req -config openssl.cnf \
    -new \
    -sha384 \
    -key intermediate.key \
    -out intermediate.csr

echo -e "${GREEN}✓ Intermediate CA CSR created${NC}"

# =============================================================================
# Step 3: Sign Intermediate CA with Root CA
# =============================================================================
echo -e "\n${GREEN}[3/4] Signing Intermediate CA with Root CA...${NC}"

cd "$ROOT_CA_DIR"

# Sign Intermediate CA certificate (2 years = 730 days)
openssl ca -config openssl.cnf \
    -extensions v3_intermediate_ca \
    -days 730 \
    -notext \
    -md sha384 \
    -batch \
    -in "$INTERMEDIATE_CA_DIR/intermediate.csr" \
    -out "$INTERMEDIATE_CA_DIR/intermediate.crt"

echo -e "${GREEN}✓ Intermediate CA signed by Root CA${NC}"

# =============================================================================
# Step 4: Create certificate chain
# =============================================================================
echo -e "\n${GREEN}[4/4] Creating certificate chain...${NC}"

# Create chain file (Intermediate + Root)
cat "$INTERMEDIATE_CA_DIR/intermediate.crt" "$ROOT_CA_DIR/root-ca.crt" \
    > "$INTERMEDIATE_CA_DIR/ca-chain.crt"

echo -e "${GREEN}✓ Certificate chain created${NC}"

# =============================================================================
# Verification
# =============================================================================
echo -e "\n${GREEN}=============================================${NC}"
echo -e "${GREEN}  PKI Initialization Complete!${NC}"
echo -e "${GREEN}=============================================${NC}"

echo -e "\n${YELLOW}Root CA Certificate:${NC}"
openssl x509 -in "$ROOT_CA_DIR/root-ca.crt" -noout -subject -issuer -dates

echo -e "\n${YELLOW}Intermediate CA Certificate:${NC}"
openssl x509 -in "$INTERMEDIATE_CA_DIR/intermediate.crt" -noout -subject -issuer -dates

echo -e "\n${YELLOW}Verifying chain...${NC}"
openssl verify -CAfile "$ROOT_CA_DIR/root-ca.crt" "$INTERMEDIATE_CA_DIR/intermediate.crt"

echo -e "\n${RED}⚠️  SECURITY WARNING:${NC}"
echo -e "${RED}The Root CA private key should be stored OFFLINE!${NC}"
echo -e "${RED}Location: $ROOT_CA_DIR/root-ca.key${NC}"
echo ""
echo -e "${GREEN}PKI is ready. You can now issue doctor certificates with:${NC}"
echo -e "  ./issue-doctor-cert.sh \"Dr. Name\" \"Hospital Name\""
