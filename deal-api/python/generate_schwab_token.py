
import requests
import base64
import os
import json
import time
import sys
import urllib.parse
import subprocess

# --- Configuration ---
ENV_FILE = "../.env"
TOKENS_FILE = "../schwab_tokens.json"

def load_env_vars():
    # 1. Try system environment variables first
    client_id = os.environ.get("SCHWAB_CLIENT_ID", "")
    client_secret = os.environ.get("SCHWAB_CLIENT_SECRET", "")
    
    # 2. Fallback to .env file if not in system environment
    if not client_id or not client_secret:
        if os.path.exists(ENV_FILE):
            with open(ENV_FILE, "r") as f:
                for line in f:
                    if line.strip().startswith("SCHWAB_CLIENT_ID="):
                        client_id = line.split("=", 1)[1].strip().strip('"')
                    elif line.strip().startswith("SCHWAB_CLIENT_SECRET="):
                        client_secret = line.split("=", 1)[1].strip().strip('"')
    
    # Optional: Prompt user if still missing
    if not client_id:
        client_id = input("Enter Schwab Client ID (App Key): ").strip()
    if not client_secret:
        client_secret = input("Enter Schwab Client Secret: ").strip()
        
    return client_id, client_secret

def refresh_tokens(client_id, client_secret):
    if not os.path.exists(TOKENS_FILE):
        print(f"Error: {TOKENS_FILE} not found. Running manual setup first...")
        request_new_tokens(client_id, client_secret)
        return

    with open(TOKENS_FILE, "r") as f:
        stored_tokens = json.load(f)
    
    refresh_token = stored_tokens.get("refresh_token")
    if not refresh_token:
        print("Error: No refresh token found. Running manual setup...")
        request_new_tokens(client_id, client_secret)
        return

    print("Refreshing tokens automatically...")
    token_url = "https://api.schwabapi.com/v1/oauth/token"
    headers = {
        "Authorization": f"Basic {base64.b64encode(f'{client_id}:{client_secret}'.encode()).decode()}",
        "Content-Type": "application/x-www-form-urlencoded"
    }
    data = {
        "grant_type": "refresh_token",
        "refresh_token": refresh_token
    }

    response = requests.post(token_url, headers=headers, data=data)
    
    if response.status_code == 200:
        tokens = response.json()
        save_tokens(tokens)
        print("✅ Tokens refreshed successfully!")
    else:
        print(f"❌ Refresh failed: {response.status_code}")
        print(response.text)
        print("\nTry running without '--refresh' to re-authorize manually.")

def request_new_tokens(client_id, client_secret):
    """Original manual flow for initial setup or full re-auth."""
    redirect_url = "https://127.0.0.1"
    print(f"\n1. Login to Schwab via this URL:\n")
    print(f"https://api.schwabapi.com/v1/oauth/authorize?client_id={client_id}&redirect_uri={redirect_url}")
    
    returned_url = input("\n2. Paste the FULL redirected URL here: ").strip()
    
    try:
        if "code=" in returned_url:
            code = urllib.parse.unquote(returned_url.split("code=")[1].split("&")[0])
        else:
            print("Error: No 'code' found in URL.")
            return
    except Exception as e:
        print(f"Error parsing URL: {e}")
        return

    token_url = "https://api.schwabapi.com/v1/oauth/token"
    headers = {
        "Authorization": f"Basic {base64.b64encode(f'{client_id}:{client_secret}'.encode()).decode()}",
        "Content-Type": "application/x-www-form-urlencoded"
    }
    data = {
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": redirect_url
    }

    print("\nRequesting initial tokens...")
    response = requests.post(token_url, headers=headers, data=data)

    if response.status_code == 200:
        save_tokens(response.json())
        print("✅ Success! tokens saved.")
    else:
        print(f"❌ Failed: {response.status_code}\n{response.text}")

def update_railway(tokens):
    """Updates the Schwab tokens in your Railway project environment."""
    print("🚀 Pushing new tokens to Railway...")
    
    variables = [
        f"SCHWAB_ACCESS_TOKEN={tokens.get('access_token')}",
        f"SCHWAB_REFRESH_TOKEN={tokens.get('refresh_token')}",
        f"SCHWAB_TOKENS_LAST_UPDATED={int(time.time())}"
    ]
    
    try:
        cmd = ["railway", "variable", "set"] + variables
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        print("✅ Railway variables updated successfully!")
    except subprocess.CalledProcessError as e:
        print(f"❌ Failed to update Railway: {e.stderr}")
    except FileNotFoundError:
        print("❌ Error: Railway CLI not found. Please install it first.")

def save_tokens(tokens):
    save_data = {
        "refresh_token": tokens.get("refresh_token"),
        "access_token": tokens.get("access_token"),
        "updated_at": int(time.time())
    }
    with open(TOKENS_FILE, "w") as f:
        json.dump(save_data, f, indent=4)
    print(f"Tokens saved to {os.path.abspath(TOKENS_FILE)}")
    
    if "--railway" in sys.argv:
        update_railway(tokens)

def main():
    print("--- Schwab API Token Manager ---")
    client_id, client_secret = load_env_vars()
    
    if not client_id or not client_secret:
        print("Error: Missing SCHWAB_CLIENT_ID or SCHWAB_CLIENT_SECRET.")
        return

    if "--refresh" in sys.argv:
        refresh_tokens(client_id, client_secret)
    else:
        request_new_tokens(client_id, client_secret)

if __name__ == "__main__":
    main()
