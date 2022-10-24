function updateRow(e, i) {
    e.parentNode.parentNode.style.color = e.checked ? "inherit" : "grey";
    $("name"+i).disabled = !e.checked;
}

// Adding an onclick listener to the link in UI
// DEV MEMO:
// We are doing it after DOM content is loaded as a good practice to ensure we are not slowing down
// the page rendering. In that particular situation the addition of the onclick handler shouldn't
// really impact the page performances, but rather stick with good practices.

document.addEventListener('DOMContentLoaded', (event) => {

    const tagCheckboxes = document.querySelectorAll("input[type=checkbox][id*=tag][name*=tag]");
    tagCheckboxes.forEach((element, index) => {
        element.onchange = (_) => updateRow(element, index);
    });

});
