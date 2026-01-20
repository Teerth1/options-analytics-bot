
import requests
import base64
import os
import json
import time

# --- Configuration ---
# You can hardcode these or load them from .env if you have python-dotenv installed
# For simplicity, we will try to read them from the file or prompt the user.

ENV_FILE = "../.env"
TOKENS_FILE = "../schwab_tokens.json"

def load_env_vars():
    client_id = ""
    client_secret = ""
    
    if os.path.exists(ENV_FILE):
        with open(ENV_FILE, "r") as f:
            for line in f:
                if line.strip().startswith("SCHWAB_CLIENT_ID="):
                    client_id = line.split("=", 1)[1].strip()
                elif line.strip().startswith("SCHWAB_CLIENT_SECRET="):
                    client_secret = line.split("=", 1)[1].strip()
    
    if not client_id:
        client_id = input("Enter Schwab Client ID (App Key): ").strip()
    if not client_secret:
        client_secret = input("Enter Schwab Client Secret: ").strip()
        
    return client_id, client_secret

def main():
    print("--- Schwab API Token Generator ---")
    client_id, client_secret = load_env_vars()
    
    if not client_id or not client_secret:
        print("Error: Missing credentials.")
        return

    # 1. Authorize logic
    redirect_url = "https://127.0.0.1"
    print(f"\n1. Login to Schwab via this URL:\n")
    print(f"https://api.schwabapi.com/v1/oauth/authorize?client_id={client_id}&redirect_uri={redirect_url}")
    print("\n2. After logging in and approving, you will be redirected to a page that might fail to load.")
    print("   Look at the URL bar of that failed page. It will look like:")
    print(f"   {redirect_url}/?code=YOUR_CODE_HERE&session=...\n")
    
    returned_url = input("3. Paste the FULL redirected URL here: ").strip()
    
    # Extract code
    try:
        if "code=" in returned_url:
            code = returned_url.split("code=")[1].split("&")[0]
            # Decode if needed (usually it's URLEncoded)
            # But requests handles the payload encoding usually. 
            # Schwab codes sometimes have %xx in them.
            import urllib.parse
            code = urllib.parse.unquote(code)
        else:
            print("Error: No 'code' found in URL.")
            return
    except Exception as e:
        print(f"Error parsing URL: {e}")
        return

    # 2. Exchange for tokens
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

    print("\nRequesting tokens...")
    response = requests.post(token_url, headers=headers, data=data)

    if response.status_code == 200:
        tokens = response.json()
        print("\n✅ SUCCESS!")
        print(f"Access Token: {tokens.get('access_token')[:20]}...")
        print(f"Refresh Token: {tokens.get('refresh_token')[:20]}...")
        
        # Save to JSON file for Java app to pick up
        save_data = {
            "refresh_token": tokens.get("refresh_token"),
            "access_token": tokens.get("access_token"), # Optional, but good to have
            "updated_at": int(time.time())
        }
        
        with open(TOKENS_FILE, "w") as f:
            json.dump(save_data, f, indent=4)
            
        print(f"\nSaved tokens to {os.path.abspath(TOKENS_FILE)}")
        print("Restart your Java application now. It will load this file automatically.")
        
    else:
        print(f"\n❌ FAILED: {response.status_code}")
        print(response.text)

if __name__ == "__main__":
    main()
