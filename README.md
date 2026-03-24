This is an Assignment demonstrating large file upload using PreSignedUrl. The steps to be followed are
1. Send POST request to the following url---https://4f5h04v0e9.execute-api.ap-south-1.amazonaws.com/Prod/generate-url
   The request body should be similar to the following example
   ```json
   {
    "fileName":"abc.txt",
    "category":"mp4"
   }
```
2.You will get a url in the response
3. Send a PUT request to the url with the file passed in the form of binary data
