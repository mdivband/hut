/**
 * ProvStore API jQuery plugin
 *
 * Example usage:
 *
 * var api = new $.provStoreApi({
 *  username: '<username>',
 *  key: '<api_key>'
 * });
 *
 * api.submitDocument("document_name", { "activity": { "a1": {} } }, function(new_document_id){console.log(new_document_id)},
 *    function(error){console.error(error)});
 *
 * api.addBundle(<target_document_id>, "bundle_identifier", { "activity": { "a1": {} } },
 *    function(){console.log('added')}, function(error){console.error(error)});
 *
 * api.getDocument(<target_document_id>, function(response){console.log(response)}, function(error){console.error(error)});
 * api.deleteDocument(<target_document_id>, function(){console.log('deleted')}, function(error){console.error(error)});
 *
 */

(function ($, undefined) {

    $.provStoreApi = function (options) {
        this.settings = $.extend({
            location: "https://provenance.ecs.soton.ac.uk/store/api/v0/"
        }, options);
    };

    $.provStoreApi.prototype._authorizationHeader = function () {
        return "ApiKey " + this.settings.username + ":" + this.settings.key;
    };

    $.provStoreApi.prototype.request = function (path, data, method, callback, err) {
        var headers = {'Accept': "application/json"};
        if (this.settings.username && this.settings.key) {
            headers.Authorization = this._authorizationHeader();
        }

        $.ajax(this.settings.location + path, {
            contentType: "application/json",
            headers: headers,
            data: data ? method == 'GET' ? data : JSON.stringify(data) : null,
            type: method || 'GET',
            error: function (jqXHR, textStatus, errorThrown) {
                err(errorThrown);
            },
            success: callback || function () {
            }
        });
    };

    $.provStoreApi.prototype.submitDocument = function (identifier, prov_document, isPublic, callback, err) {
        if (isPublic === undefined) {
            isPublic = false;
        }
        var data = {
            'content': prov_document,
            'public': isPublic,
            'rec_id': identifier || null
        };

        this.request('documents/', data, 'POST', function (response) {
            callback(response.id);
        }, err);
    };

    $.provStoreApi.prototype.getDocuments = function (offset, limit, callback, err) {
        var data = {};
        if (offset) {
            data.offset = offset;
        }
        if (limit) {
            data.limit = limit;
        }
        this.request('documents/', data, 'GET', callback, err);
    };

    $.provStoreApi.prototype.getDocument = function (id, callback, err) {
        this.request('documents/' + id + '/', null, 'GET', callback, err);
    };

    $.provStoreApi.prototype.getDocumentBody = function (id, format, callback, err) {
        this.request('documents/' + id + '.' + format, null, 'GET', callback, err);
    };

    $.provStoreApi.prototype.addBundle = function (id, identifier, prov_bundle, callback, err) {
        var data = {
            'content': prov_bundle,
            'rec_id': identifier
        };

        this.request('documents/' + id + '/bundles/', data, 'POST', function (response) {
            callback(response);
        }, err);
    };

    $.provStoreApi.prototype.deleteDocument = function (id, callback, err) {
        this.request('documents/' + id + '/', null, 'DELETE', callback, err);
    };

}(jQuery));