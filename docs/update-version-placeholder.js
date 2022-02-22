// See https://stackoverflow.com/q/43262121/
// and https://stackoverflow.com/q/36975619

const URL = "https://api.github.com/repos/mahozad/android-pie-chart/releases/latest"
const PLACEHOLDER = /<version>|&lt;version&gt;/g

fetch(URL)
  .then(response => response.json())
  .then(jsonResponse => {
    const version = jsonResponse["tag_name"].substring(1);
    document.body.innerHTML = document.body.innerHTML.replace(PLACEHOLDER, version);
  });
