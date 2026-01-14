#!/bin/bash

# Colors for logs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}  MediVault Sequential Build${NC}"
echo -e "${BLUE}================================${NC}\n"

# Function to display errors
error_exit() {
    echo -e "${RED}✗ Error: $1${NC}" >&2
    exit 1
}

# Function to display success
success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Function to display info
info() {
    echo -e "${YELLOW}ℹ  $1${NC}"
}

# Check if docker compose is installed
if ! command -v docker &> /dev/null; then
    error_exit "Docker is not installed"
fi

# Stop all existing containers
info "Stopping existing containers..."
sudo docker compose down
success "Containers stopped"

echo ""

# Clean old images (optional)
read -p "Do you want to clean Docker cache? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    info "Cleaning Docker cache..."
    sudo docker system prune -f
    success "Cache cleaned"
fi

echo -e "\n${BLUE}================================${NC}"
echo -e "${BLUE}  1/5 - Build Elasticsearch${NC}"
echo -e "${BLUE}================================${NC}\n"
sudo docker compose build elasticsearch || error_exit "Elasticsearch build failed"
success "Elasticsearch built"

echo -e "\n${BLUE}================================${NC}"
echo -e "${BLUE}  2/5 - Build Kibana${NC}"
echo -e "${BLUE}================================${NC}\n"
sudo docker compose build kibana || error_exit "Kibana build failed"
success "Kibana built"

echo -e "\n${BLUE}================================${NC}"
echo -e "${BLUE}  3/5 - Build LogServer${NC}"
echo -e "${BLUE}================================${NC}\n"
sudo docker compose build logserver || error_exit "LogServer build failed"
success "LogServer built"

echo -e "\n${BLUE}================================${NC}"
echo -e "${BLUE}  4/5 - Build MediVault Server${NC}"
echo -e "${BLUE}================================${NC}\n"
sudo docker compose build medivault-server || error_exit "MediVault Server build failed"
success "MediVault Server built"

echo -e "\n${BLUE}================================${NC}"
echo -e "${BLUE}  5/5 - Build Filebeat${NC}"
echo -e "${BLUE}================================${NC}\n"
sudo docker compose build filebeat || error_exit "Filebeat build failed"
success "Filebeat built"

echo -e "\n${GREEN}================================${NC}"
echo -e "${GREEN}  All builds successful!${NC}"
echo -e "${GREEN}================================${NC}\n"

# Ask if we want to start services
read -p "Do you want to start the services now? (Y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
    info "Starting services..."
    sudo docker compose up -d
    
    echo ""
    success "Services started!"
    echo ""
    info "Check logs with: sudo docker compose logs -f"
    echo ""
    echo "Available services:"
    echo "  • MediVault:     https://localhost:8443"
    echo "  • Kibana:        http://localhost:5601"
    echo "  • Elasticsearch: http://localhost:9200"
    echo "  • LogServer:     localhost:5555 (TCP)"
fi

echo ""