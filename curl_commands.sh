#!/bin/bash

# First, create the group
echo "Creating group 'Trip'..."
curl -X POST 'http://localhost:8080/api/groups' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Trip",
    "description": "Group for trip expenses"
}'

# Wait for a moment to ensure group is created
sleep 1

# Add all members to the group
echo "Adding members to the group..."
curl -X POST 'http://localhost:8080/api/groups/members' \
  -H 'Content-Type: application/json' \
  -d '{
    "groupId": 1,
    "memberIds": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]
}'

echo "Done!"
