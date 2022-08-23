function revokeApiToken(anchorRevoke) {
    const repeatedChunk = anchorRevoke.up('.repeated-chunk');
    const apiTokenList = repeatedChunk.up('.api-token-list');
    const confirmMessage = anchorRevoke.getAttribute('data-confirm');
    const targetUrl = anchorRevoke.getAttribute('data-target-url');
    const inputUuid = repeatedChunk.querySelector('.api-token-uuid-input');
    const apiTokenUuid = inputUuid.value;

    if (confirm(confirmMessage)) {
        new Ajax.Request(targetUrl, {
            method: "post",
            parameters: {apiTokenUuid: apiTokenUuid},
            onSuccess: function(res, _) {
                repeatedChunk.remove();
                adjustEmptyListMessage(apiTokenList);
            }
        });
    }

    return false;
}

function saveApiToken(button){
    if (button.hasClassName('request-pending')) {
        // avoid multiple requests to be sent if user is clicking multiple times
        return;
    }
    button.addClassName('request-pending');
    const targetUrl = button.getAttribute('data-target-url');
    const repeatedChunk = button.up('.repeated-chunk');
    const apiTokenList = repeatedChunk.up('.api-token-list');
    const nameInput = repeatedChunk.querySelector('.api-token-name-input');
    const apiTokenName = nameInput.value;

    new Ajax.Request(targetUrl, {
        method: "post",
        parameters: {apiTokenName: apiTokenName},
        onSuccess: function(res, _) {
            const { name, value, uuid } = res.responseJSON.data;
            nameInput.value = name;

            const apiTokenValueSpan = repeatedChunk.querySelector('.new-api-token-value');
            apiTokenValueSpan.innerText = value;
            apiTokenValueSpan.removeClassName('hidden');

            const apiTokenCopyButton = repeatedChunk.querySelector('.copy-button');
            apiTokenCopyButton.setAttribute('text', value);
            apiTokenCopyButton.removeClassName('hidden');

            const uuidInput = repeatedChunk.querySelector('.api-token-uuid-input');
            uuidInput.value = uuid;

            const warningMessage = repeatedChunk.querySelector('.api-token-warning-message');
            warningMessage.removeClassName('hidden');

            // we do not want to allow user to create twice api token using same name by mistake
            button.remove();

            const revokeButton = repeatedChunk.querySelector('.api-token-revoke-button');
            revokeButton.removeClassName('hidden');

            const cancelButton = repeatedChunk.querySelector('.api-token-cancel-button');
            cancelButton.addClassName('hidden');

            repeatedChunk.addClassName('api-token-list-fresh-item');

            adjustEmptyListMessage(apiTokenList);
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
