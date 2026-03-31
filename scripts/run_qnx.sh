#!/bin/bash

############################################
# Usage:
# ./push_to_queue.sh /path/to/file.c
############################################

C_FILE=$1

QUEUE_URL="https://sqs.us-east-2.amazonaws.com/160927904719/code-execution-queue"
REGION="us-east-2"

############################################
# VALIDATION
############################################

if [ -z "$C_FILE" ]; then
    echo "Usage: ./push_to_queue.sh <path_to_c_file>"
    exit 1
fi

if [ ! -f "$C_FILE" ]; then
    echo "Error: File not found"
    exit 1
fi

############################################
# GENERATE JOB ID
############################################

JOB_ID=$(uuidgen)

############################################
# READ FILE CONTENT
############################################

CODE=$(cat "$C_FILE")

############################################
# ESCAPE JSON (IMPORTANT)
############################################

ESCAPED_CODE=$(printf '%s' "$CODE" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')

############################################
# BUILD JSON PAYLOAD
############################################

BODY=$(cat <<EOF
{
  "jobId": "$JOB_ID",
  "code": $ESCAPED_CODE
}
EOF
)

############################################
# SEND TO SQS
############################################

echo "Sending job: $JOB_ID"

aws sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$BODY" \
    --region "$REGION"

if [ $? -eq 0 ]; then
    echo "✅ Job pushed successfully"
else
    echo "❌ Failed to push job"
fi
