#!/bin/bash
#
# RSA 4096-bit Key Pair Generator for JWT RS256
#
# Usage:
#   ./scripts/generate-rsa-keys.sh [output-dir]
#
# Security Notes:
#   - NEVER commit private keys to git
#   - Store private keys in secure vault (AWS Secrets Manager, HashiCorp Vault, etc.)
#   - Distribute public keys safely to all services
#   - Rotate keys every 90 days
#
# Reference: OWASP JWT Cheat Sheet for Java
# https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default output directory
OUTPUT_DIR="${1:-./keys}"
PRIVATE_KEY="$OUTPUT_DIR/jwt-private-key.pem"
PUBLIC_KEY="$OUTPUT_DIR/jwt-public-key.pem"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}RSA 4096-bit Key Pair Generator${NC}"
echo -e "${GREEN}========================================${NC}"
echo

# Check if openssl is installed
if ! command -v openssl &> /dev/null; then
    echo -e "${RED}ERROR: openssl is not installed${NC}"
    echo "Install with: apt-get install openssl (Ubuntu/Debian)"
    echo "            or: brew install openssl (macOS)"
    exit 1
fi

# Create output directory if it doesn't exist
if [ ! -d "$OUTPUT_DIR" ]; then
    echo -e "${YELLOW}Creating output directory: $OUTPUT_DIR${NC}"
    mkdir -p "$OUTPUT_DIR"
fi

# Check if keys already exist
if [ -f "$PRIVATE_KEY" ] || [ -f "$PUBLIC_KEY" ]; then
    echo -e "${YELLOW}WARNING: Keys already exist in $OUTPUT_DIR${NC}"
    echo -e "${YELLOW}Existing keys:${NC}"
    [ -f "$PRIVATE_KEY" ] && echo "  - $PRIVATE_KEY"
    [ -f "$PUBLIC_KEY" ] && echo "  - $PUBLIC_KEY"
    echo
    read -p "Overwrite existing keys? (yes/no): " CONFIRM
    if [ "$CONFIRM" != "yes" ]; then
        echo -e "${RED}Aborted. Keys not generated.${NC}"
        exit 0
    fi
    echo
fi

# Generate RSA 4096-bit private key
echo -e "${GREEN}[1/4] Generating RSA 4096-bit private key...${NC}"
openssl genpkey -algorithm RSA \
    -pkeyopt rsa_keygen_bits:4096 \
    -out "$PRIVATE_KEY"

# Set restrictive permissions on private key (0600 = owner read/write only)
chmod 600 "$PRIVATE_KEY"
echo -e "${GREEN}✓ Private key generated: $PRIVATE_KEY (permissions: 0600)${NC}"
echo

# Extract public key from private key
echo -e "${GREEN}[2/4] Extracting public key from private key...${NC}"
openssl rsa -pubout \
    -in "$PRIVATE_KEY" \
    -out "$PUBLIC_KEY"

# Set public key permissions (0644 = owner read/write, others read)
chmod 644 "$PUBLIC_KEY"
echo -e "${GREEN}✓ Public key extracted: $PUBLIC_KEY (permissions: 0644)${NC}"
echo

# Verify keys
echo -e "${GREEN}[3/4] Verifying key pair integrity...${NC}"

# Check private key modulus
PRIVATE_MODULUS=$(openssl rsa -noout -modulus -in "$PRIVATE_KEY" | openssl md5)
PUBLIC_MODULUS=$(openssl rsa -pubin -noout -modulus -in "$PUBLIC_KEY" | openssl md5)

if [ "$PRIVATE_MODULUS" = "$PUBLIC_MODULUS" ]; then
    echo -e "${GREEN}✓ Key pair verification: PASSED${NC}"
else
    echo -e "${RED}✗ Key pair verification: FAILED${NC}"
    echo -e "${RED}ERROR: Private and public key moduli do not match!${NC}"
    exit 1
fi

# Display key information
PRIVATE_KEY_SIZE=$(openssl rsa -text -noout -in "$PRIVATE_KEY" 2>&1 | grep "Private-Key:" | awk '{print $2}')
echo -e "${GREEN}✓ Private key size: $PRIVATE_KEY_SIZE${NC}"
echo

# Display key fingerprints
echo -e "${GREEN}[4/4] Key fingerprints:${NC}"
echo -e "${YELLOW}Private key SHA-256:${NC}"
openssl rsa -in "$PRIVATE_KEY" -pubout -outform DER 2>/dev/null | openssl dgst -sha256 | awk '{print "  " $2}'
echo -e "${YELLOW}Public key SHA-256:${NC}"
openssl rsa -pubin -in "$PUBLIC_KEY" -pubout -outform DER 2>/dev/null | openssl dgst -sha256 | awk '{print "  " $2}'
echo

# Display security warnings
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}SECURITY WARNINGS${NC}"
echo -e "${YELLOW}========================================${NC}"
echo -e "${RED}⚠️  NEVER commit private keys to git${NC}"
echo -e "${RED}⚠️  Store private keys in secure vault:${NC}"
echo "   - AWS Secrets Manager"
echo "   - HashiCorp Vault"
echo "   - Azure Key Vault"
echo "   - GCP Secret Manager"
echo
echo -e "${YELLOW}⚠️  Add to .gitignore:${NC}"
echo "   *.pem"
echo "   *.key"
echo "   src/main/resources/keys/"
echo
echo -e "${YELLOW}⚠️  Rotate keys every 90 days${NC}"
echo -e "${YELLOW}⚠️  Use kid (key ID) header for rotation:${NC}"
echo "   security.jwt.key-id=key-$(date +%Y-%m)"
echo

# Display next steps
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}NEXT STEPS${NC}"
echo -e "${GREEN}========================================${NC}"
echo "1. Update application.yml:"
echo "   security.jwt.private-key-path: file://$PRIVATE_KEY"
echo "   security.jwt.public-key-path: file://$PUBLIC_KEY"
echo "   security.jwt.key-id: key-$(date +%Y-%m)"
echo
echo "2. Deploy private key to secure vault"
echo
echo "3. Distribute public key to all services"
echo
echo "4. Restart application"
echo
echo "5. Verify with curl:"
echo "   curl -X POST http://localhost:8080/api/v1/auth/login \\"
echo "        -H 'Content-Type: application/json' \\"
echo "        -d '{\"email\":\"user@example.com\",\"password\":\"password\"}'"
echo
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Key generation completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
