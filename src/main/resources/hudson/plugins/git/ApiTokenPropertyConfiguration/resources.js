/**
* Registering the onclick handler on all the "Revoke" buttons for API Token.
*/
Behaviour.specify(".api-token-revoke-button", 'ApiTokenPropertyConfiguration', 0, function(button) {
    // DEV MEMO:
    // While un-inlining the onclick handler, we are trying to avoid modifying the existing source code and functions.
    // In order to keep consistency with the existing code, we share the api-token-revoke-button with the revokeApiToken method
    // which is then navigating the DOM in order to retrieve a the token to revoke.
    // While this could be done setting additional data on the button itself and retrieving it without DOM navigation,
    // this would need to be done in another contribution.
    button.onclick = (_) => revokeApiToken(button);
})

function revokeApiToken(anchorRevoke) {
    const repeatedChunk = anchorRevoke.closest('.repeated-chunk');
    const apiTokenList = repeatedChunk.closest('.api-token-list');
    const confirmMessage = anchorRevoke.getAttribute('data-confirm');
    const targetUrl = anchorRevoke.getAttribute('data-target-url');
    const inputUuid = repeatedChunk.querySelector('.api-token-uuid-input');
    const apiTokenUuid = inputUuid.value;

    if (confirm(confirmMessage)) {
        fetch(url + "?" + new URLSearchParams({apiTokenUuid: apiTokenUuid}), {
            headers: crumb.wrap({}),
            method: "post",
        }).then((rsp) => {
            if (rsp.ok) {
                repeatedChunk.remove();
                adjustEmptyListMessage(apiTokenList);
            }
        });
    }

    return false;
}

/**
* Registering the onclick handler on all the "Generate" buttons for API Token.
*/
Behaviour.specify(".api-token-save-button", 'ApiTokenPropertyConfiguration', 0, function(buttonContainer) {
    // DEV MEMO:
    // While un-inlining the onclick handler, we are trying to avoid modifying the existing source code and functions.
    // In order to keep consistency with the existing code, we add our onclick handler on the button element which is contained in the
    // api-token-save-button that we identify. While this could be refactored to directly identify the button, this would need to be done in an other
    // contribution.
    const button = buttonContainer.getElementsByTagName('button')[0];
    button.onclick = (_) => saveApiToken(button);
})

function saveApiToken(button){
    if (button.classList.contains('request-pending')) {
        // avoid multiple requests to be sent if user is clicking multiple times
        return;
    }
    button.classList.add('request-pending');
    const targetUrl = button.getAttribute('data-target-url');
    const repeatedChunk = button.closest('.repeated-chunk');
    const apiTokenList = repeatedChunk.closest('.api-token-list');
    const nameInput = repeatedChunk.querySelector('.api-token-name-input');
    const apiTokenName = nameInput.value;

    fetch(targetUrl + "?" + new URLSearchParams({apiTokenName: apiTokenName}), {
        headers: crumb.wrap({}),
        method: "post",
    }).then((rsp) => {
        if (rsp.ok) {
            rsp.json().then((json) => {
                const { name, value, uuid } = json.data;
                nameInput.value = name;

                const apiTokenValueSpan = repeatedChunk.querySelector('.new-api-token-value');
                apiTokenValueSpan.innerText = value;
                apiTokenValueSpan.classList.remove('hidden');

                const apiTokenCopyButton = repeatedChunk.querySelector('.copy-button');
                apiTokenCopyButton.setAttribute('text', value);
                apiTokenCopyButton.classList.remove('hidden');

                const uuidInput = repeatedChunk.querySelector('.api-token-uuid-input');
                uuidInput.value = uuid;

                const warningMessage = repeatedChunk.querySelector('.api-token-warning-message');
                warningMessage.classList.remove('hidden');

                // we do not want to allow user to create twice api token using same name by mistake
                button.remove();

                const revokeButton = repeatedChunk.querySelector('.api-token-revoke-button');
                revokeButton.classList.remove('hidden');

                const cancelButton = repeatedChunk.querySelector('.api-token-cancel-button');
                cancelButton.classList.add('hidden');

                repeatedChunk.classList.add('api-token-list-fresh-item');

                adjustEmptyListMessage(apiTokenList);
            });
        }
    });
}

function adjustEmptyListMessage(apiTokenList) {
    const emptyListMessageClassList = apiTokenList.querySelector('.api-token-list-empty-item').classList;

    const apiTokenListLength = apiTokenList.querySelectorAll('.api-token-list-existing-item, .api-token-list-fresh-item').length;
    if (apiTokenListLength >= 1) {
        emptyListMessageClassList.add("hidden");
    } else {
        emptyListMessageClassList.remove("hidden");
    }
}
